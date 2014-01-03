package org.radargun;

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

import org.radargun.config.MasterConfig;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.MasterState;

/**
 * This is the master that will coordinate the {@link Slave}s in order to run the benchmark.
 *
 * @author Mircea.Markus@jboss.com
 */
public class Master {

   private static Log log = LogFactory.getLog(Master.class);
   public static final int DEFAULT_PORT = 2103;

   MasterConfig masterConfig;

   private ServerSocketChannel serverSocketChannel;
   private SocketChannel[] slaves;

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
      slaves = new SocketChannel[masterConfig.getSlaveCount()];
      try {
         communicationSelector = Selector.open();
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Master process"));
   }

   public void start() throws Exception {
      try {
         startServerSocket();
         runDiscovery();
         prepareNextStage();
         startCommunicationWithSlaves();
      } catch (Exception e) {
         log.error("Exception in start: ", e);
      } finally {
         releaseResources();
      }
   }

   private void prepareNextStage() throws Exception {
      DistStage toExecute = state.getNextDistStageToProcess();
      if (toExecute == null) {
         releaseResourcesAndExit();
      } else {
         runDistStage(toExecute, toExecute.getActiveSlaveCount());
      }
   }

   private void runDistStage(DistStage currentStage, int noSlaves) throws Exception {
      writeBufferMap.clear();
      DistStage toSerialize;
      for (int i = 0; i < noSlaves; i++) {
         SocketChannel slave = slaves[i];
         slave.configureBlocking(false);
         slave.register(communicationSelector, SelectionKey.OP_WRITE);
         toSerialize = currentStage.clone();
         toSerialize.initOnMaster(state, i);
         if (i == 0) {//only log this once
            if (log.isDebugEnabled())
               log.debug("Starting '" + toSerialize.getClass().getSimpleName() + "' on " + toSerialize.getActiveSlaveCount() + " slave nodes. Details: " + toSerialize);
            else
               log.info("Starting '" + toSerialize.getClass().getSimpleName() + "' on " + toSerialize.getActiveSlaveCount() + " slave nodes.");
         }
         byte[] bytes = SerializationHelper.prepareForSerialization(toSerialize);
         writeBufferMap.put(slave, ByteBuffer.wrap(bytes));
      }
   }

   private void releaseResources() {
      try {
         discoverySelector.close();
      } catch (Throwable e) {
         log.warn("Error closing discovery selector", e);
      }
      try {
         communicationSelector.close();
      } catch (Throwable e) {
         log.warn("Error closing comunication selector", e);
      }
      for (SocketChannel sc : slaves) {
         try {
            sc.socket().close();
         } catch (Throwable e) {
            log.warn("Error closing channel", e);
         }
      }

      try {
         if (serverSocketChannel != null) serverSocketChannel.socket().close();
      } catch (Throwable e) {
         log.warn("Error closing server socket channel", e);
      }
   }

   private void runDiscovery() throws IOException {
      discoverySelector = Selector.open();
      serverSocketChannel.register(discoverySelector, SelectionKey.OP_ACCEPT);
      int slaveCount = 0;
      while (slaveCount < masterConfig.getSlaveCount()) {
         log.info("Awaiting registration from " + (masterConfig.getSlaveCount() - slaveCount) + " slaves.");
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
            
            int slaveIndex = readIndex(socketChannel);
            if (slaveIndex < 0) {
               for (int i = 0; i < slaves.length; ++i) {
                  if (slaves[i] == null) {
                     slaveIndex = i;
                     break;
                  }
               }
            } else if (slaveIndex >= slaves.length || slaves[slaveIndex] != null) {
               throw new IllegalArgumentException("Slave requests invalid slaveIndex " + slaveIndex);            
            }
            slaves[slaveIndex] = socketChannel;
            slaveCount++;
            slave2Index.put(socketChannel, slaveIndex);
            this.readBufferMap.put(socketChannel, ByteBuffer.allocate(DEFAULT_READ_BUFF_CAPACITY));
            if (log.isTraceEnabled())
               log.trace("Added new slave connection from: " + socketChannel.socket().getInetAddress());
         }
      }
      log.info("Connection established from " + slaveCount + " slaves.");
   }

   private int readIndex(SocketChannel socketChannel) throws IOException {      
      ByteBuffer slaveIndexBuffer = ByteBuffer.allocate(4);
      while (slaveIndexBuffer.hasRemaining()) {
         socketChannel.read(slaveIndexBuffer);      
      }
      slaveIndexBuffer.flip();
      return slaveIndexBuffer.getInt();
   }

   private void startCommunicationWithSlaves() throws Exception {
      while (true) {
         communicationSelector.select();
         Set<SelectionKey> keys = communicationSelector.selectedKeys();
         if (keys.size() > 0) {
            Iterator<SelectionKey> keysIt = keys.iterator();
            while (keysIt.hasNext()) {
               SelectionKey key = keysIt.next();
               keysIt.remove();
               if (!key.isValid()) {
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

   private void readStageAck(SelectionKey key) throws Exception {
      SocketChannel socketChannel = (SocketChannel) key.channel();

      ByteBuffer byteBuffer = readBufferMap.get(socketChannel);
      int value = socketChannel.read(byteBuffer);

      if (value == -1) {
         Integer slaveIndex = slave2Index.get(socketChannel);
         log.warn("Slave stopped! Index: " + slaveIndex + ". Remote socket is: " + socketChannel);
         key.cancel();
         if (slaveIndex == null || slaves[slaveIndex] != socketChannel) {
            throw new IllegalStateException("Socket " + socketChannel + " should have been there!");
         }
         releaseResourcesAndExit();
      } else if (byteBuffer.position() >= 4) {
         int expectedSize = byteBuffer.getInt(0);
         if ((expectedSize + 4) > byteBuffer.capacity()) {
            ByteBuffer replacer = ByteBuffer.allocate(expectedSize + 4);
            replacer.put(byteBuffer.array(), 0, byteBuffer.position());
            readBufferMap.put(socketChannel, replacer);
            if (log.isTraceEnabled())
               log.trace("Expected size(" + expectedSize + ")" + " is > ByteBuffer's capacity(" +
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
      releaseResources();
      ShutDownHook.exit(0);
   }

   private void sendStage(SelectionKey key) throws IOException {
      SocketChannel socketChannel = (SocketChannel) key.channel();
      ByteBuffer buf = writeBufferMap.get(socketChannel);
      socketChannel.write(buf);
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
      log.info("Waiting 5 seconds for server socket to open completely");
      try 
      {
      	Thread.sleep(5000);
      } catch (InterruptedException ex)
      {
          // ignore
      }
   }
}
