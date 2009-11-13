package org.cachebench;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.config.FixedSizeBenchmarkConfig;
import org.cachebench.config.MasterConfig;
import org.cachebench.config.ConfigHelper;
import org.cachebench.config.jaxb.BenchConfig;
import org.cachebench.stages.ClearClusterStage;
import org.cachebench.stages.ClusterValidationStage;
import org.cachebench.stages.CsvReportGenerationStage;
import org.cachebench.stages.StartClusterStage;
import org.cachebench.stages.WarmupStage;
import org.cachebench.stages.WebSessionBenchmarkStage;
import org.cachebench.state.MasterState;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is the master that will coordonate the {@link Slave}s in order to run the benchmark.
 * TODO - in discovery, the number of slaves can be determined from max scale
 * TODO - by default lookupt the config file in classpath
 * TODO - use mcast discovery
 *    - on each network interface broadcast on a certain multicast address and receive the nio IP and port
 *
 * @author Mircea.Markus@jboss.com
 */
public class Master {

   private static Log log = LogFactory.getLog(Master.class);
   public static final int DEFAULT_PORT = 2103;

   MasterConfig masterConfig;

   private ServerSocketChannel serverSocketChannel;
   private List<SocketChannel> slaves = new ArrayList<SocketChannel>();

   private volatile boolean stopped = false;

   private Map<SocketChannel, ByteBuffer> writeBufferMap = new HashMap<SocketChannel, ByteBuffer>();
   private Map<SocketChannel, ByteBuffer> readBufferMap = new HashMap<SocketChannel, ByteBuffer>();
   private List<DistStageAck> responses = new ArrayList<DistStageAck>();
   private Selector communicationSelector;
   private Selector discoverySelector;
   private Map<SocketChannel, Integer> slave2Index = new HashMap<SocketChannel, Integer>();
   private MasterState state;
   int processedSlaves = 0;
   private static final int DEFAULT_READ_BUFF_CAPACITY = 1024;

   public Master(MasterConfig masterConfig) {
      this.masterConfig = masterConfig;
      state = new MasterState(masterConfig);
      try {
         communicationSelector = Selector.open();
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   public void start() throws Exception {
      try {
         startServerSocket();
         runDiscovery();
         prepareNextStage();
         startCommunicationWithSlaves();
      } finally {
         releseResources();
      }
   }

   private void prepareNextStage() throws Exception {
      DistStage toExecute = state.getNextDistStageToProcess();
      if (toExecute == null) {
         releaseResourcesAndExit();
      }
      Set<Integer> slaves = state.getSlaveIndexesForCurrentStage();
      runDistStage(toExecute, slaves);
   }

   private void runDistStage(DistStage currentStage, Set<Integer> slaveIndexes) throws Exception {
      writeBufferMap.clear();
      DistStage toSerialize;
      for (Integer index : slaveIndexes) {
         SocketChannel slave = slaves.get(index);
         slave.configureBlocking(false);
         slave.register(communicationSelector, SelectionKey.OP_WRITE);
         toSerialize = currentStage.clone();
         toSerialize.setSlaveIndex(index);
         byte[] bytes = SerializationHelper.prepareForSerialization(toSerialize);
         writeBufferMap.put(slave, ByteBuffer.wrap(bytes));
      }
   }

   private void releseResources() {
      try {
         discoverySelector.close();
      } catch (Throwable e) {
         log.warn(e);
      }
      try {
         communicationSelector.close();
      } catch (Throwable e) {
         log.warn(e);
      }
      for (SocketChannel sc : slaves) {
         try {
            sc.socket().close();
         } catch (Throwable e) {
            log.warn(e);
         }
      }

      try {
         if (serverSocketChannel != null) serverSocketChannel.socket().close();
      } catch (Throwable e) {
         log.warn(e);
      }
   }

   private void runDiscovery() throws IOException {
      discoverySelector = Selector.open();
      serverSocketChannel.register(discoverySelector, SelectionKey.OP_ACCEPT);
      while (slaves.size() < masterConfig.getSlaveCount()) {
         log.info("Waiting for " + (masterConfig.getSlaveCount() - slaves.size()) + " slaves to register to the master.");
         discoverySelector.select();
         Set<SelectionKey> keySet = discoverySelector.selectedKeys();
         Iterator<SelectionKey> it = keySet.iterator();
         while (it.hasNext()) {
            SelectionKey selectionKey = it.next();
            it.remove();
            if (!selectionKey.isValid()) {
               continue;
            }
            ServerSocketChannel srvSocketChannel = (ServerSocketChannel) selectionKey.channel();
            SocketChannel socketChannel = srvSocketChannel.accept();
            slaves.add(socketChannel);
            slave2Index.put(socketChannel, (slaves.size() - 1));
            this.readBufferMap.put(socketChannel, ByteBuffer.allocate(DEFAULT_READ_BUFF_CAPACITY));
            if (log.isTraceEnabled())
               log.trace("Added new slave connection from: " + socketChannel.socket().getInetAddress());
         }
      }
      log.info("Connection established from " + slaves.size() + " slaves.");
   }

   private void startCommunicationWithSlaves() throws Exception {
      while (!stopped) {
         communicationSelector.select();
         Set<SelectionKey> keys = communicationSelector.selectedKeys();
         if (log.isTraceEnabled()) log.trace("Received " + keys.size() + " keys.");
         if (keys.size() > 0) {
            Iterator<SelectionKey> keysIt = keys.iterator();
            while (keysIt.hasNext()) {
               SelectionKey key = keysIt.next();
               keysIt.remove();
               if (!key.isValid()) {
                  log.trace("Key not valid, skipping!");
                  continue;
               }
               if (key.isWritable()) {
                  if (log.isTraceEnabled()) log.trace("Received writable key:" + key);
                  sendStage(key);
               } else if (key.isReadable()) {
                  if (log.isTraceEnabled()) log.trace("Received readable key:" + key);
                  readStageAck(key);
               } else {
                  log.warn("Unknown selection on key " + key);
               }
            }
         }
      }
   }

   private void readStageAck(SelectionKey key) throws Exception {
      SocketChannel socketChannel = (SocketChannel) key.channel();

      ByteBuffer byteBuffer = readBufferMap.get(socketChannel);
      int value = socketChannel.read(byteBuffer);
      if (log.isTraceEnabled()) {
         log.trace("We've read into the buffer: " + byteBuffer + ". Number of read bytes is " + value);
      }

      if (value == -1) {
         log.warn("Slave stopped! Index: " + slave2Index.get(socketChannel) + ". Remote socket is: " + socketChannel);
         key.cancel();
         if (!slaves.remove(socketChannel)) {
            throw new IllegalStateException("Socket " + socketChannel + " should have been there!");
         }
      } else if (byteBuffer.limit() >= 4) {
         int expectedSize = byteBuffer.getInt(0);
         if ((expectedSize + 4) > byteBuffer.capacity()) {
            ByteBuffer replacer = ByteBuffer.allocate(expectedSize + 4);
            replacer.put(byteBuffer.array());
            readBufferMap.put(socketChannel, replacer);
            if (log.isTraceEnabled())
               log.trace("Expected size(" + expectedSize + ")" + " is > bytebuffer's capacity(" +
                     byteBuffer.capacity() + ")" + ".Replacing " + byteBuffer + " with " + replacer);
            byteBuffer = replacer;
         }
         if (log.isTraceEnabled())
            log.trace("Expected size: " + expectedSize + ". byteBuffer.position() == " + byteBuffer.position());
         if (byteBuffer.position() == expectedSize + 4) {
            log.trace("Received ACK from " + socketChannel);
            DistStageAck ack = (DistStageAck) SerializationHelper.deserialize(byteBuffer.array(), 4, expectedSize);
            byteBuffer.clear();
            responses.add(ack);
         }
      }

      if (responses.size() == state.getSlavesCountForCurrentStage()) {
         if (!state.distStageFinished(responses)) {
            log.error("Exiting because issues processing current stage: " + state.getCurrentDistStage());
            releaseResourcesAndExit();
         }
         prepareNextStage();
      }
   }

   private void releaseResourcesAndExit() {
      releseResources();
      System.exit(0);
   }

   private void sendStage(SelectionKey key) throws IOException {
      SocketChannel socketChannel = (SocketChannel) key.channel();
      ByteBuffer buf = writeBufferMap.get(socketChannel);
      if (log.isTraceEnabled())
         log.trace("Writing buffer '" + buf + " to channel '" + socketChannel + "' ");
      socketChannel.write(buf);
      if (log.isTraceEnabled())
         log.trace("Buffer after write: '" + buf + "'");
      if (buf.remaining() == 0) {
         log.trace("Finished writing entire buffer");
         key.interestOps(SelectionKey.OP_READ);
         processedSlaves++;
         if (log.isTraceEnabled())
            log.trace("Current stage successfully transmitted to " + processedSlaves + " slave(s).");
      }
      if (processedSlaves == state.getSlavesCountForCurrentStage()) {
         log.trace("Successfully completed broadcasting stage " + state.getCurrentDistStage());
         processedSlaves = 0;
         writeBufferMap.clear();
         responses.clear();
      }
   }

   private void startServerSocket() throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.configureBlocking(false);
      InetSocketAddress address;
      if (masterConfig.getHost() == null) {
         address = new InetSocketAddress(masterConfig.getPort());
      } else {
         address = new InetSocketAddress(masterConfig.getHost(), masterConfig.getPort());
      }
      serverSocketChannel.socket().bind(address);
      log.info("Master started and listening for connection on: " + address);
   }

   public static void main(String[] args) throws Exception {

      String config = null;

      for (int i = 0; i < args.length - 1; i++) {
         if (args[i].equals("-config")) {
            config = args[i + 1];
         }
      }

      if (config == null) {
         printUsageAndExit();
      }
      File configFile = new File(config);
      if (!configFile.exists()) {
         System.err.println("No such file: " + configFile.getAbsolutePath());
         printUsageAndExit();
      }
//      ScalingBenchmarkConfig sc = new ScalingBenchmarkConfig();
//      createStages(sc);
//      sc.setInitSize(2);
//      sc.setMaxSize(4);
//      sc.setName("scaling");
//
//      FixedSizeBenchmarkConfig fixedBenchConfig = new FixedSizeBenchmarkConfig();
//      createStages(fixedBenchConfig);
//      fixedBenchConfig.setName("fixed");
//
//      MasterConfig masterConfig = new MasterConfig(1234, "127.0.0.1",4);
//      masterConfig.addBenchmark(sc);
//      masterConfig.addBenchmark(fixedBenchConfig);
//      new Master(masterConfig).start();


      JAXBContext jc = JAXBContext.newInstance("org.cachebench.config.jaxb");
      Unmarshaller unmarshaller = jc.createUnmarshaller();
      BenchConfig benchConfig = (BenchConfig) unmarshaller.unmarshal(configFile);
      Master server = ConfigHelper.getMaster(benchConfig);
      server.start();

//      MasterConfig masterConfig = new MasterConfig(1234, "127.0.0.1", 2);
//      for (int i=0; i < 100; i++) {
//         masterConfig.addStage(new DummyStage("NAME" + i + i + i + i));
//      }
//      masterConfig.addStage(new DummyStage("SECOND"));
//
//
//      Master server2 = new Master(masterConfig);
//      server2.start();
   }

   private static void createStages(FixedSizeBenchmarkConfig sc) {
      List<Stage> stages = new ArrayList<Stage>();
      StartClusterStage scs = new StartClusterStage();
      scs.setChacheWrapperClass("org.cachebench.cachewrappers.InfinispanWrapper");
      Map<String, String> props = Collections.singletonMap("config", "dist-sync.xml");
      scs.setWrapperStartupParams(props);
      stages.add(scs);


      ClusterValidationStage cvs = new ClusterValidationStage();
      cvs.setPartialReplication(false);
      stages.add(cvs);


      WarmupStage ws = new WarmupStage();
      ws.setOperationCount(200);
      stages.add(ws);

      WebSessionBenchmarkStage wsbs = new WebSessionBenchmarkStage();
      wsbs.setNumberOfRequests(1000);
      stages.add(wsbs);

      CsvReportGenerationStage csvrg = new CsvReportGenerationStage();
      stages.add(csvrg);

      ClearClusterStage ccs = new ClearClusterStage();
      stages.add(ccs);

      sc.setStages(stages);
   }

   private static void printUsageAndExit() {
      System.out.println("Usage: start_master.sh  -config <config-file.xml>");
      System.out.println("       -config : xml file containing benchmark's configuration");
      System.exit(1);
   }
}
