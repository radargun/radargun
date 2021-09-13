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
import org.radargun.utils.WorkerConnectionInfo;

/**
 * Connection to workers in different JVMs
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RemoteWorkerConnection {

   private static final long CONNECT_TIMEOUT = TimeUnit.MINUTES.toMillis(5);
   private static Log log = LogFactory.getLog(RemoteWorkerConnection.class);

   private static final int UUID_BYTES = 16;
   private static final int EXPECTED_SIZE_BYTES = 4;
   private static final int DEFAULT_WRITE_BUFF_CAPACITY = 1024;
   private static final int DEFAULT_READ_BUFF_CAPACITY = 1024;

   public static final int DEFAULT_PORT = 2103;

   private ServerSocketChannel serverSocketChannel;
   private WorkerRecord[] workers;
   private WorkerAddresses workerAddresses;

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

   private static class WorkerRecord {
      private UUID uuid; // key unique for given series of generations of this worker
      private SocketChannel channel;

      public WorkerRecord(int index, UUID uuid, SocketChannel channel) {
         this.uuid = uuid;
         this.channel = channel;
      }
   }

   /**
    * Holds information about interfaces and IP addresses of individual workers.
    * This information is collected from individual workers and re-distributed to the other
    * workers in the cluster.
    */
   public static class WorkerAddresses implements Serializable {
      public Map<Integer, WorkerConnectionInfo> workerConnections;

      public WorkerAddresses() {
         this.workerConnections = new HashMap<>();
      }

      public void addWorkerAddresses(int index, WorkerConnectionInfo connectionInfo) {
         workerConnections.put(index, connectionInfo);
      }

      public WorkerConnectionInfo getWorkerAddresses(int index) {
         return workerConnections.get(index);
      }
   }

   public RemoteWorkerConnection(int numWorkers, String host, int port) throws IOException {
      this.host = host;
      this.port = port > 0 && port < 65536 ? port : DEFAULT_PORT;
      workers = new WorkerRecord[numWorkers];
      workerAddresses = new WorkerAddresses();
      communicationSelector = Selector.open();
      startServerSocket();
   }

   public void establish() throws IOException {
      discoverySelector = Selector.open();
      serverSocketChannel.register(discoverySelector, SelectionKey.OP_ACCEPT);
      mcastBuffer = ByteBuffer.allocate(DEFAULT_WRITE_BUFF_CAPACITY);
      int workerCount = 0;
      long deadline = TimeService.currentTimeMillis() + CONNECT_TIMEOUT;
      while (workerCount < workers.length) {
         long timeout = deadline - TimeService.currentTimeMillis();
         if (timeout <= 0) {
            throw new IOException((workers.length - workerCount) + " workers haven't connected within timeout!");
         }
         log.info("Awaiting registration from " + (workers.length - workerCount) + " workers.");
         workerCount += connectWorkers(timeout);
      }
      log.info("Connection established from " + workerCount + " workers.");
   }

   private int connectWorkers(long timeout) throws IOException {
      discoverySelector.select(timeout);
      Set<SelectionKey> keySet = discoverySelector.selectedKeys();
      Iterator<SelectionKey> it = keySet.iterator();
      int workerCount = 0;
      while (it.hasNext()) {
         SelectionKey selectionKey = it.next();
         it.remove();
         if (!selectionKey.isValid()) {
            continue;
         }
         ServerSocketChannel srvSocketChannel = (ServerSocketChannel) selectionKey.channel();
         SocketChannel socketChannel = srvSocketChannel.accept();

         int workerIndex = readInt(socketChannel);
         ByteBuffer uuidBytes = readBytes(socketChannel, UUID_BYTES);
         UUID uuid = new UUID(uuidBytes.getLong(), uuidBytes.getLong());

         if (workerIndex < 0) {
            for (int i = 0; i < workers.length; ++i) {
               if (workers[i] == null) {
                  workerIndex = i;
                  break;
               }
            }
            if (workerIndex < 0) {
               throw new IllegalArgumentException("All workers are already connected.");
            }
         } else if (workerIndex >= workers.length) {
            throw new IllegalArgumentException("Worker requests invalid workerIndex "
               + workerIndex + " (expected " + workers.length + " workers)");
         }
         if (workers[workerIndex] == null) {
            if (uuid.getLeastSignificantBits() != 0 || uuid.getMostSignificantBits() != 0) {
               throw new IllegalArgumentException("We are expecting 0th generation worker " + workerIndex + " but it already has UUID set!");
            }
            workers[workerIndex] = new RemoteWorkerConnection.WorkerRecord(workerIndex, uuid, socketChannel);
         } else if (workers[workerIndex] != null) {
            RemoteWorkerConnection.WorkerRecord record = workers[workerIndex];
            if (!uuid.equals(record.uuid)) {
               throw new IllegalArgumentException(String.format("For worker %d expecting UUID %s but new generation (%s) has UUID %s",
                  workerIndex, record.uuid, socketChannel, uuid));
            }
            record.channel = socketChannel;
         }
         writeInt(socketChannel, workerIndex);
         writeInt(socketChannel, workers.length);

         workerCount++;
         channel2Index.put(socketChannel, workerIndex);
         readBufferMap.put(socketChannel, ByteBuffer.allocate(DEFAULT_READ_BUFF_CAPACITY));
         socketChannel.configureBlocking(false);
         log.trace("Added new worker connection " + workerIndex + " from: " + socketChannel.socket().getInetAddress());
      }
      return workerCount;
   }

   public void sendScenario(Scenario scenario, int clusterSize) throws IOException {
      mcastObject(scenario, clusterSize);
      flushBuffers(0);
   }

   public void sendConfiguration(Configuration configuration) throws IOException {
      mcastObject(configuration, workers.length);
      flushBuffers(0);
   }

   public void sendCluster(Cluster cluster) throws IOException {
      mcastObject(cluster, cluster.getSize());
      flushBuffers(0);
   }

   private void clearBuffer() {
      if (!writeBufferMap.isEmpty()) {
         throw new IllegalStateException("Something not sent to workers yet: " + writeBufferMap);
      }
      mcastBuffer.clear();
   }

   private void mcastBuffer(int numWorkers) throws IOException {
      for (int i = 0; i < numWorkers; ++i) {
         SocketChannel channel = workers[i].channel;
         if (channel == null) throw new IOException("Worker " + i + " disconnected");
         writeBufferMap.put(channel, ByteBuffer.wrap(mcastBuffer.array(), 0, mcastBuffer.position()));
         channel.register(communicationSelector, SelectionKey.OP_WRITE);
      }
   }

   private void mcastObject(Serializable object, int numWorkers) throws IOException {
      clearBuffer();
      mcastBuffer = SerializationHelper.serializeObjectWithLength(object, mcastBuffer);
      mcastBuffer(numWorkers);
   }

   public List<DistStageAck> runStage(int stageId, Map<String, Object> mainData, int numWorkers) throws IOException {
      responses.clear();
      clearBuffer();
      mcastBuffer.putInt(stageId);
      mcastBuffer = SerializationHelper.serializeObjectWithLength((Serializable) mainData, mcastBuffer);
      mcastBuffer(numWorkers);
      flushBuffers(numWorkers);
      ArrayList<DistStageAck> list = new ArrayList<>(responses.size());
      for (Object o : responses) {
         list.add((DistStageAck) o);
      }
      return list;
   }

   public List<Timeline> receiveTimelines(int numWorkers) throws IOException {
      responses.clear();
      mcastObject(new Timeline.Request(), numWorkers);
      flushBuffers(numWorkers);
      return Arrays.asList(responses.toArray(new Timeline[numWorkers]));
   }

   public void receiveWorkerAddresses() throws IOException {
      responses.clear();
      mcastObject(new WorkerConnectionInfo.Request(), workers.length);
      flushBuffers(workers.length);
      List<WorkerConnectionInfo> connections = Arrays.asList(responses.toArray(new WorkerConnectionInfo[workers.length]));
      for (WorkerConnectionInfo connectionInfo : connections) {
         workerAddresses.addWorkerAddresses(connectionInfo.getWorkerIndex(), connectionInfo);
      }
   }

   public void sendWorkerAddresses() throws IOException {
      mcastObject(workerAddresses, workers.length);
      flushBuffers(0);
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
         log.infof("Waiting for %d reconnecting workers", reconnections);
         long timeout = deadline - TimeService.currentTimeMillis();
         if (timeout <= 0) {
            throw new IOException(reconnections + " workers haven't connected within timeout!");
         }
         reconnections -= connectWorkers(timeout);
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
               log.tracef("Worker %d (%s) is going to restart with UUID %s", index, socketChannel.getRemoteAddress(), uuid);
               WorkerRecord record = workers[index];
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
         Integer workerIndex = channel2Index.get(socketChannel);
         key.cancel();
         if (workerIndex == null) {
            throw new IllegalStateException("Unknown worker for socket " + socketChannel);
         }
         WorkerRecord record = workers[workerIndex];
         if (record.channel == null) {
            // this channel was closed correctly
            return;
         } else if (record.channel != socketChannel) {
            throw new IllegalStateException("Unexpected socket channel " + socketChannel + "; should be " + record.channel);
         } else {
            log.warn("Worker stopped! Index: " + workerIndex + ". Remote socket is: " + socketChannel);
            throw new IOException("Worker unexpectedly stopped");
         }
      }
   }

   public void release() {
      if (mcastBuffer != null) {
         try {
            mcastObject(null, workers.length);
            flushBuffers(0);
         } catch (Exception e) {
            log.warn("Failed to send termination to workers.", e);
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
      for (WorkerRecord record : workers) {
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

   public void restartWorkers(int numWorkers) throws IOException {
      responses.clear();
      mcastObject(new Restart(), numWorkers);
      flushBuffers(numWorkers);
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
      log.info("Attempting to start Main listening for connection on: " + address);
      serverSocketChannel.socket().bind(address);
      log.info("Waiting 15 seconds for server socket to open completely");
      try {
         Thread.sleep(15000);
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

   private void writeInt(SocketChannel socketChannel, int workerIndex) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
      buffer.putInt(workerIndex);
      buffer.flip();
      while (buffer.hasRemaining()) {
         socketChannel.write(buffer);
      }
   }

   public static class Restart implements Serializable {}
}
