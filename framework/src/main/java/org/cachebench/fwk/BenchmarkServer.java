package org.cachebench.fwk;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.fwk.config.BenchConfig;
import org.cachebench.fwk.config.ConfigHelper;
import org.cachebench.fwk.state.ServerState;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * // TODO: Mircea - Document this! todo - handle node crashes and server crashes todo -consider using MC for keeping
 * state between stages on client and server
 *
 * @author Mircea.Markus@jboss.com
 */
public class BenchmarkServer {

   private static Log log = LogFactory.getLog(BenchmarkServer.class);
   public static final int DEFAULT_PORT = 2103;

   ServerConfig serverConfig;

   private ServerSocketChannel serverSocketChannel;
   private List<SocketChannel> nodes = new ArrayList<SocketChannel>();

   private volatile boolean stopped = false;

   private List<Stage> remainingStages;
   private Stage currentStage;
   private Map<SocketChannel, ByteBuffer> bufferMap = new HashMap<SocketChannel, ByteBuffer>();
   private List<DistStageAck> responses = new ArrayList<DistStageAck>();
   private AtomicInteger processedNodes = new AtomicInteger();
   private Selector communicationSelector;
   private Selector discoverySelector;
   private Map<SocketChannel, Integer> node2Index = new HashMap<SocketChannel, Integer>();
   private ServerState state;

   public BenchmarkServer(ServerConfig serverConfig) {
      this.serverConfig = serverConfig;
      state = new ServerState(serverConfig);
      try {
         communicationSelector = Selector.open();
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   public void start() throws IOException {
      try {
         startServerSocket();
         runDiscovery();
         initBuffers();
         startCommunicationWithNodes();
      } finally {
         releseResources();
      }
   }

   private void initBuffers() throws IOException {
      remainingStages = new ArrayList<Stage>(serverConfig.getStages());
      assert remainingStages.size() > 0;
      prepareNextStage();
   }

   private void prepareNextStage() throws IOException {
      if (remainingStages.size() == 0) {
         releaseResourcesAndExit();
      }
      currentStage = remainingStages.remove(0);
      while (currentStage instanceof ServerStage) {
         runServerStage((ServerStage) currentStage);
         if (remainingStages.isEmpty()) {
            log.info("No more stages to run, exiting...");
            releaseResourcesAndExit();
         }
         currentStage = remainingStages.remove(0);
      }
      runDistStage((DistStage) currentStage);
   }

   private void runServerStage(ServerStage currentStage) {
      log.info("Starting server stage " + currentStage);
      currentStage.init(state);
      if (!currentStage.execute()) {
         log.warn("Server stage hasn't executed successfully");
      }
   }

   private void runDistStage(DistStage currentStage) throws IOException {
      currentStage.initOnServer(state);
      log.info("Starting distributed stage " + currentStage);
      bufferMap.clear();
      DistStage toSerialize;
      for (SocketChannel node : nodes) {
         try {
            node.configureBlocking(false);
            SelectionKey prevKey = node.register(communicationSelector, SelectionKey.OP_WRITE);
            if (prevKey != null && prevKey.interestOps() != SelectionKey.OP_READ) {
               log.warn("I was supposed to be interested only in reads at this stage!");
            }
            toSerialize = currentStage.clone();
         } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
         }
         toSerialize.setNodeIndex(node2Index.get(node));
         byte[] bytes = SerializationHelper.prepareForSerialization(toSerialize);
         bufferMap.put(node, ByteBuffer.wrap(bytes));
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
      for (SocketChannel sc : nodes) {
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
      while (nodes.size() < serverConfig.getNodeCount()) {
         log.info("Waiting for " + (serverConfig.getNodeCount() - nodes.size()) + " nodes to register to the server.");
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
            nodes.add(socketChannel);
            node2Index.put(socketChannel, (nodes.size() - 1));
            if (log.isTraceEnabled())
               log.trace("Added new node connection from: " + socketChannel.socket().getInetAddress());
         }
      }
      log.info("Connection established from " + nodes.size() + " nodes.");
   }

   private void startCommunicationWithNodes() throws IOException {
      while (!stopped) {
         communicationSelector.select();
         Set<SelectionKey> keys = communicationSelector.selectedKeys();
         if (log.isTraceEnabled()) log.trace("Received " + keys.size() + " keys.");
         if (keys.size() > 0) {
            Iterator<SelectionKey> keysIt = keys.iterator();
            while (keysIt.hasNext()) {
               SelectionKey key = keysIt.next();
               if (log.isTraceEnabled())
                  log.trace("Processing key: " + key);
               keysIt.remove();
               if (!key.isValid()) {
                  log.trace("Key not valid, skipping!");
                  continue;
               }
               if (key.isWritable()) {
                  sendStage(key);
               } else if (key.isReadable()) {
                  readStageAck(key);
               } else {
                  log.warn("Unknown selection on key " + key);
               }
            }
         }
      }
   }

   private void readStageAck(SelectionKey key) throws IOException {
      SocketChannel socketChannel = (SocketChannel) key.channel();

      ByteBuffer byteBuffer = bufferMap.get(socketChannel);

      if (byteBuffer == null) {
         byteBuffer = ByteBuffer.allocate(8192);
         bufferMap.put(socketChannel, byteBuffer);
      }

      socketChannel.read(byteBuffer);

      if (byteBuffer.limit() >= 4) {
         int expectedSize = byteBuffer.getInt(0);
         if (byteBuffer.position() == expectedSize + 4) {
            log.trace("Received ACK from " + socketChannel);
            DistStageAck ack = (DistStageAck) SerializationHelper.deserialize(byteBuffer.array(), 4, expectedSize);
            responses.add(ack);
         }
         if (responses.size() == nodes.size()) {
            if (!((DistStage) currentStage).processAckOnServer(responses)) {
               log.warn("Current stage determined failures while iterating through server responses. Exiting the framework.");
               releaseResourcesAndExit();
            }
            log.info("Successfully finished stage " + currentStage);
            if (remainingStages.size() == 0) {
               log.info("Finished all stages, exiting!");
               releaseResourcesAndExit();
            }
            prepareNextStage();
         }
      }
   }

   private void releaseResourcesAndExit() {
      releseResources();
      System.exit(0);
   }

   private void sendStage(SelectionKey key) throws IOException {
      SocketChannel socketChannel = (SocketChannel) key.channel();
      ByteBuffer buf = bufferMap.get(socketChannel);
      log.trace("Writing buffer '" + buf + " to channel '" + socketChannel + "' ");
      socketChannel.write(buf);
      log.trace("Buffer after write: '" + buf + "'");
      if (buf.remaining() == 0) {
         log.trace("Finished writing entire buffer");
         key.interestOps(SelectionKey.OP_READ);
         int nodes = processedNodes.incrementAndGet();
         if (log.isTraceEnabled())
            log.trace(currentStage + " successfully transmitted to " + nodes + " node(s).");
      }
      if (processedNodes.get() == nodes.size()) {
         log.info("Successfully completed broadcasting stage: " + currentStage);
         processedNodes.set(0);
         bufferMap.clear();
         responses.clear();
      }
   }

   private void startServerSocket() throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.configureBlocking(false);
      InetSocketAddress address;
      if (serverConfig.getHost() == null) {
         address = new InetSocketAddress(serverConfig.getPort());
      } else {
         address = new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort());
      }
      serverSocketChannel.socket().bind(address);
      log.info("BenchmarkServer started and listening for connection on: " + address);
   }

   public static void main(String[] args) throws Exception {

      String config = null;

      for (int i = 0; i < args.length - 1; i++) {
         if (args[i].equals("-config")) {
            config = args[i + 1];
         } else {
            System.out.println("Unknown param name: " + args[i]);
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

      JAXBContext jc = JAXBContext.newInstance("org.cachebench.fwk.config");
      Unmarshaller unmarshaller = jc.createUnmarshaller();


      BenchConfig benchConfig = (BenchConfig) unmarshaller.unmarshal(configFile);
      BenchmarkServer server = ConfigHelper.getServer(benchConfig);
      server.start();

//      ServerConfig serverConfig = new ServerConfig(serverPort, serverHost, nodesCount);
//
//      StartClusterStage startClusterStage = new StartClusterStage();
//      startClusterStage.setChacheWrapperClass("org.cachebench.cachewrappers.InfinispanWrapper");
//      Map startUpParams = new HashMap();
//      startUpParams.put("config", "repl-sync.xml");
//      startClusterStage.setWrapperStartupParams(startUpParams);
//      serverConfig.addStage(startClusterStage);
//
//      ClusterValidationStage clusterValidationStage = new ClusterValidationStage();
//      serverConfig.addStage(clusterValidationStage);
//
//      WarmupStage warmupStage = new WarmupStage();
//      serverConfig.addStage(warmupStage);
//
//      WebSessionBenchmarkStage webSessionBenchmarkStage = new WebSessionBenchmarkStage();
//      webSessionBenchmarkStage.setNumberOfRequests(100000);
//      serverConfig.addStage(webSessionBenchmarkStage);
//
//      CsvReportGenerationStage csvReportGenerationStage = new CsvReportGenerationStage();
//      serverConfig.addStage(csvReportGenerationStage);
//
//      BenchmarkFinishedStage benchmarkFinishedStage = new BenchmarkFinishedStage();
//      serverConfig.addStage(benchmarkFinishedStage);


//      for (int i=0; i < 100; i++) {
//         serverConfig.addStage(new DummyStage("NAME" + i + i + i + i));
//      }
//      serverConfig.addStage(new DummyStage("SECOND"));
//

//      BenchmarkServer server = new BenchmarkServer(serverConfig);
//      server.start();
   }

   private static void printUsageAndExit() {
      System.out.println("Usage: start_master.sh  -config <config-file.xml>");
      System.out.println("       -config : xml file containing benchmark's configuration");
      System.exit(1);
   }
}
