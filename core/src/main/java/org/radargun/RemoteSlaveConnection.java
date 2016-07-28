package org.radargun;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.utils.TimeService;

/**
 * Connection to slaves in different JVMs
 */
public class RemoteSlaveConnection {

   private static final long CONNECT_TIMEOUT = TimeUnit.MINUTES.toMillis(5);
   private static Log log = LogFactory.getLog(RemoteSlaveConnection.class);

   private static final int UUID_BYTES = 16;
   private static final int EXPECTED_SIZE_BYTES = 4;
   private static final int DEFAULT_WRITE_BUFF_CAPACITY = 1024;
   private static final int DEFAULT_READ_BUFF_CAPACITY = 1024;

   public static final int DEFAULT_PORT = 2103;

   private ServerSocketChannel serverSocketChannel;
   private SlaveRecord[] slaves;

   private ByteBuffer mcastBuffer;
   private Map<SocketChannel, ByteBuffer> writeBufferMap = new HashMap<SocketChannel, ByteBuffer>();
   private Map<SocketChannel, ByteBuffer> readBufferMap = new HashMap<SocketChannel, ByteBuffer>();
   private List<Object> responses = new ArrayList<Object>();
   private Selector communicationSelector;
   private Selector discoverySelector;
   private Map<SocketChannel, Integer> channel2Index = new HashMap<>();
   private int reconnections = 0;

   private String host;
   private int port;

   private static class SlaveRecord {
      private int index; // slave id, should be equal to index in slaves field
      private UUID uuid; // key unique for given series of generations of this slave
      private SocketChannel channel;

      public SlaveRecord(int index, UUID uuid, SocketChannel channel) {
         this.index = index;
         this.uuid = uuid;
         this.channel = channel;
      }
   }

   public RemoteSlaveConnection(int numSlaves, String host, int port) throws IOException {
      this.host = host;
      this.port = port > 0 && port < 65536 ? port : DEFAULT_PORT;
      slaves = new SlaveRecord[numSlaves];
      communicationSelector = Selector.open();
      startServerSocket();
   }

   public void establish() throws IOException {
      discoverySelector = Selector.open();
      serverSocketChannel.register(discoverySelector, SelectionKey.OP_ACCEPT);
      int slaveCount = 0;
      long deadline = TimeService.currentTimeMillis() + CONNECT_TIMEOUT;
      while (slaveCount < slaves.length) {
         long timeout = deadline - TimeService.currentTimeMillis();
         if (timeout <= 0) {
            throw new IOException((slaves.length - slaveCount) + " slaves haven't connected within timeout!");
         }
         log.info("Awaiting registration from " + (slaves.length - slaveCount) + " slaves.");
         slaveCount += connectSlaves(timeout);
      }
      mcastBuffer = ByteBuffer.allocate(DEFAULT_WRITE_BUFF_CAPACITY);
      log.info("Connection established from " + slaveCount + " slaves.");
   }

   private int connectSlaves(long timeout) throws IOException {
      discoverySelector.select(timeout);
      Set<SelectionKey> keySet = discoverySelector.selectedKeys();
      Iterator<SelectionKey> it = keySet.iterator();
      int slaveCount = 0;
      while (it.hasNext()) {
         SelectionKey selectionKey = it.next();
         it.remove();
         if (!selectionKey.isValid()) {
            continue;
         }
         ServerSocketChannel srvSocketChannel = (ServerSocketChannel) selectionKey.channel();
         SocketChannel socketChannel = srvSocketChannel.accept();

         int slaveIndex = readInt(socketChannel);
         ByteBuffer uuidBytes = readBytes(socketChannel, UUID_BYTES);
         UUID uuid = new UUID(uuidBytes.getLong(), uuidBytes.getLong());

         if (slaveIndex < 0) {
            for (int i = 0; i < slaves.length; ++i) {
               if (slaves[i] == null) {
                  slaveIndex = i;
                  break;
               }
            }
            if (slaveIndex < 0) {
               throw new IllegalArgumentException("All slaves are already connected.");
            }
         } else if (slaveIndex >= slaves.length) {
            throw new IllegalArgumentException("Slave requests invalid slaveIndex "
               + slaveIndex + " (expected " + slaves.length + " slaves)");
         }
         if (slaves[slaveIndex] == null) {
            if (uuid.getLeastSignificantBits() != 0 || uuid.getMostSignificantBits() != 0) {
               throw new IllegalArgumentException("We are expecting 0th generation slave " + slaveIndex + " but it already has UUID set!");
            }
            slaves[slaveIndex] = new RemoteSlaveConnection.SlaveRecord(slaveIndex, uuid, socketChannel);
         } else if (slaves[slaveIndex] != null) {
            RemoteSlaveConnection.SlaveRecord record = slaves[slaveIndex];
            if (!uuid.equals(record.uuid)) {
               throw new IllegalArgumentException(String.format("For slave %d expecting UUID %s but new generation (%s) has UUID %s",
                  slaveIndex, record.uuid, socketChannel, uuid));
            }
            record.channel = socketChannel;
         }
         writeInt(socketChannel, slaveIndex);
         writeInt(socketChannel, slaves.length);
         slaveCount++;
         channel2Index.put(socketChannel, slaveIndex);
         readBufferMap.put(socketChannel, ByteBuffer.allocate(DEFAULT_READ_BUFF_CAPACITY));
         socketChannel.configureBlocking(false);
         log.trace("Added new slave connection " + slaveIndex + " from: " + socketChannel.socket().getInetAddress());
      }
      return slaveCount;
   }

   public void sendScenario(Scenario scenario, int clusterSize) throws IOException {
      mcastObject(scenario, clusterSize);
      flushBuffers(0);
   }

   public void sendConfiguration(Configuration configuration) throws IOException {
      mcastObject(configuration, slaves.length);
      flushBuffers(0);
   }

   public void sendCluster(Cluster cluster) throws IOException {
      mcastObject(cluster, cluster.getSize());
      flushBuffers(0);
   }

   private void clearBuffer() {
      if (!writeBufferMap.isEmpty()) {
         throw new IllegalStateException("Something not sent to slaves yet: " + writeBufferMap);
      }
      mcastBuffer.clear();
   }

   private void mcastBuffer(int numSlaves) throws IOException {
      for (int i = 0; i < numSlaves; ++i) {
         SocketChannel channel = slaves[i].channel;
         if (channel == null) throw new IOException("Slave " + i + " disconnected");
         writeBufferMap.put(channel, ByteBuffer.wrap(mcastBuffer.array(), 0, mcastBuffer.position()));
         channel.register(communicationSelector, SelectionKey.OP_WRITE);
      }
   }

   private void mcastObject(Serializable object, int numSlaves) throws IOException {
      clearBuffer();
      mcastBuffer = SerializationHelper.serializeObjectWithLength(object, mcastBuffer);
      mcastBuffer(numSlaves);
   }

   public List<DistStageAck> runStage(int stageId, Map<String, Object> masterData, int numSlaves) throws IOException {
      responses.clear();
      clearBuffer();
      mcastBuffer.putInt(stageId);
      mcastBuffer = SerializationHelper.serializeObjectWithLength((Serializable) masterData, mcastBuffer);
      mcastBuffer(numSlaves);
      flushBuffers(numSlaves);
      ArrayList<DistStageAck> list = new ArrayList<>(responses.size());
      for (Object o : responses) {
         list.add((DistStageAck) o);
      }
      return list;
   }

   public List<Timeline> receiveTimelines(int numSlaves) throws IOException {
      responses.clear();
      mcastObject(new Timeline.Request(), numSlaves);
      flushBuffers(numSlaves);
      return Arrays.asList(responses.toArray(new Timeline[numSlaves]));
   }

   private void flushBuffers(int numResponses) throws IOException {
      while (!writeBufferMap.isEmpty() || responses.size() < numResponses) {
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
                  sendData(key);
               } else if (key.isReadable()) {
                  readResponse(key);
               } else {
                  log.warn("Unknown selection on key " + key);
               }
            }
         }
      }
      long deadline = TimeService.currentTimeMillis() + CONNECT_TIMEOUT;
      while (reconnections > 0) {
         log.infof("Waiting for %d reconnecting slaves", reconnections);
         long timeout = deadline - TimeService.currentTimeMillis();
         if (timeout <= 0) {
            throw new IOException(reconnections + " slaves haven't connected within timeout!");
         }
         reconnections -= connectSlaves(timeout);
      }
   }

   private void sendData(SelectionKey key) throws IOException {
      SocketChannel socketChannel = (SocketChannel) key.channel();
      ByteBuffer buf = writeBufferMap.get(socketChannel);
      socketChannel.write(buf);
      if (buf.remaining() == 0) {
         key.interestOps(SelectionKey.OP_READ);
         writeBufferMap.remove(socketChannel);
         log.trace("Finished writing entire buffer, " + writeBufferMap.size() + " write buffers remaining.");
      }
   }

   private void readResponse(SelectionKey key) throws IOException {
      SocketChannel socketChannel = (SocketChannel) key.channel();

      ByteBuffer byteBuffer = readBufferMap.get(socketChannel);
      int value = socketChannel.read(byteBuffer);

      if (byteBuffer.position() >= EXPECTED_SIZE_BYTES) {
         int expectedSize = byteBuffer.getInt(0);
         if ((expectedSize + EXPECTED_SIZE_BYTES + UUID_BYTES) > byteBuffer.capacity()) {
            ByteBuffer replacer = ByteBuffer.allocate(expectedSize + EXPECTED_SIZE_BYTES + UUID_BYTES);
            replacer.put(byteBuffer.array(), 0, byteBuffer.position());
            readBufferMap.put(socketChannel, replacer);
            if (log.isTraceEnabled())
               log.trace("Expected size(" + expectedSize + ")" + " is > ByteBuffer's capacity(" +
                  byteBuffer.capacity() + ")" + ".Replacing " + byteBuffer + " with " + replacer);
            byteBuffer = replacer;
         }
         if (log.isTraceEnabled())
            log.trace("Expected size: " + expectedSize + ". byteBuffer.position() == " + byteBuffer.position());
         if (byteBuffer.position() >= expectedSize + EXPECTED_SIZE_BYTES + UUID_BYTES) {
            log.trace("Received response from " + socketChannel.getRemoteAddress());
            Object response = SerializationHelper.deserialize(byteBuffer.array(), EXPECTED_SIZE_BYTES, expectedSize);
            long uuidMsb = byteBuffer.getLong(EXPECTED_SIZE_BYTES + expectedSize);
            long uuidLsb = byteBuffer.getLong(EXPECTED_SIZE_BYTES + expectedSize + 8);
            if (uuidMsb != 0 && uuidLsb != 0) {
               // we should expect reconnection
               int index = channel2Index.get(socketChannel);
               UUID uuid = new UUID(uuidMsb, uuidLsb);
               log.tracef("Slave %d (%s) is going to restart with UUID %s", index, socketChannel.getRemoteAddress(), uuid);
               SlaveRecord record = slaves[index];
               record.uuid = uuid;
               record.channel.close();
               record.channel = null;
               channel2Index.remove(socketChannel);
               readBufferMap.remove(socketChannel);
               reconnections++;
            }
            byteBuffer.clear();
            responses.add(response);
         }
      }
      if (value < 0) {
         Integer slaveIndex = channel2Index.get(socketChannel);
         key.cancel();
         if (slaveIndex == null) {
            throw new IllegalStateException("Unknown slave for socket " + socketChannel);
         }
         SlaveRecord record = slaves[slaveIndex];
         if (record.channel == null) {
            // this channel was closed correctly
            return;
         } else if (record.channel != socketChannel) {
            throw new IllegalStateException("Unexpected socket channel " + socketChannel + "; should be " + record.channel);
         } else {
            log.warn("Slave stopped! Index: " + slaveIndex + ". Remote socket is: " + socketChannel);
            throw new IOException("Slave unexpectedly stopped");
         }
      }
   }

   public void release() {
      if (mcastBuffer != null) {
         try {
            mcastObject(null, slaves.length);
            flushBuffers(0);
         } catch (Exception e) {
            log.warn("Failed to send termination to slaves.", e);
         }
      }
      if (discoverySelector != null) {
         try {
            discoverySelector.close();
         } catch (Throwable e) {
            log.warn("Error closing discovery selector", e);
         }
      }
      if (communicationSelector != null) {
         try {
            communicationSelector.close();
         } catch (Throwable e) {
            log.warn("Error closing comunication selector", e);
         }
      }
      for (SlaveRecord record : slaves) {
         if (record != null && record.channel != null) {
            try {
               record.channel.close();
            } catch (Throwable e) {
               log.warn("Error closing channel", e);
            }
         }
      }

      if (serverSocketChannel != null) {
         try {
            serverSocketChannel.socket().close();
         } catch (Throwable e) {
            log.warn("Error closing server socket channel", e);
         }
      }
   }

   public void restartSlaves(int numSlaves) throws IOException {
      responses.clear();
      mcastObject(new Restart(), numSlaves);
      flushBuffers(numSlaves);
   }

   private void startServerSocket() throws IOException {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.configureBlocking(false);
      InetSocketAddress address;
      if (host == null) {
         address = new InetSocketAddress(port);
      } else {
         address = new InetSocketAddress(host, port);
      }
      serverSocketChannel.socket().bind(address);
      log.info("Master started and listening for connection on: " + address);
      log.info("Waiting 5 seconds for server socket to open completely");
      try {
         Thread.sleep(5000);
      } catch (InterruptedException ex) {
         // ignore
      }
   }

   private int readInt(SocketChannel socketChannel) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
      while (buffer.hasRemaining()) {
         socketChannel.read(buffer);
      }
      buffer.flip();
      return buffer.getInt();
   }

   private ByteBuffer readBytes(SocketChannel socketChannel, int numBytes) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(numBytes);
      while (buffer.hasRemaining()) {
         socketChannel.read(buffer);
      }
      buffer.flip();
      return buffer;
   }

   private void writeInt(SocketChannel socketChannel, int slaveIndex) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
      buffer.putInt(slaveIndex);
      buffer.flip();
      while (buffer.hasRemaining()) {
         socketChannel.write(buffer);
      }
   }

   public static class Restart implements Serializable {}
}
