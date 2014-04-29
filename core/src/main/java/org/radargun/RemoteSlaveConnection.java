package org.radargun;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
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

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;

/**
 * Connection to slaves in different JVMs
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RemoteSlaveConnection implements SlaveConnection {

   private static Log log = LogFactory.getLog(RemoteSlaveConnection.class);

   private static final int DEFAULT_WRITE_BUFF_CAPACITY = 1024;
   private static final int DEFAULT_READ_BUFF_CAPACITY = 1024;
   public static final int DEFAULT_PORT = 2103;

   private ServerSocketChannel serverSocketChannel;
   private SocketChannel[] slaves;

   private ByteBuffer mcastBuffer;
   private Map<SocketChannel, ByteBuffer> writeBufferMap = new HashMap<SocketChannel, ByteBuffer>();
   private Map<SocketChannel, ByteBuffer> readBufferMap = new HashMap<SocketChannel, ByteBuffer>();
   private List<Object> responses = new ArrayList<Object>();
   private Selector communicationSelector;
   private Selector discoverySelector;
   private Map<SocketChannel, Integer> slave2Index = new HashMap<SocketChannel, Integer>();

   private String host;
   private int port;

   public RemoteSlaveConnection(int numSlaves, String host, int port) throws IOException {
      this.host = host;
      this.port = port > 0 && port < 65536 ? port : DEFAULT_PORT;
      slaves = new SocketChannel[numSlaves];
      communicationSelector = Selector.open();
      startServerSocket();
   }

   @Override
   public void establish() throws IOException {
      discoverySelector = Selector.open();
      serverSocketChannel.register(discoverySelector, SelectionKey.OP_ACCEPT);
      int slaveCount = 0;
      while (slaveCount < slaves.length) {
         log.info("Awaiting registration from " + (slaves.length - slaveCount) + " slaves.");
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

            int slaveIndex = readInt(socketChannel);
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
            writeInt(socketChannel, slaveIndex);
            writeInt(socketChannel, slaves.length);
            slaves[slaveIndex] = socketChannel;
            slaveCount++;
            slave2Index.put(socketChannel, slaveIndex);
            this.readBufferMap.put(socketChannel, ByteBuffer.allocate(DEFAULT_READ_BUFF_CAPACITY));
            socketChannel.configureBlocking(false);
            log.trace("Added new slave connection " + slaveIndex + " from: " + socketChannel.socket().getInetAddress());
         }
      }
      mcastBuffer = ByteBuffer.allocate(DEFAULT_WRITE_BUFF_CAPACITY);
      log.info("Connection established from " + slaveCount + " slaves.");
   }

   @Override
   public void sendScenario(Scenario scenario) throws IOException {
      mcastObject(scenario, slaves.length);
      flushBuffers(0);
   }

   @Override
   public void sendConfiguration(Configuration configuration) throws IOException {
      mcastObject(configuration, slaves.length);
      flushBuffers(0);
   }

   @Override
   public void sendCluster(Cluster cluster) throws IOException {
      mcastObject(cluster, cluster.getSize());
      flushBuffers(0);
   }

   private void mcastObject(Serializable object, int numSlaves) throws IOException {
      if (!writeBufferMap.isEmpty()) {
         throw new IllegalStateException("Something not sent to slaves yet: " + writeBufferMap);
      }
      mcastBuffer.clear();
      mcastBuffer = SerializationHelper.serializeObjectWithLength(object, mcastBuffer);
      for (int i = 0; i < numSlaves; ++i) {
         writeBufferMap.put(slaves[i], ByteBuffer.wrap(mcastBuffer.array(), 0, mcastBuffer.position()));
         slaves[i].register(communicationSelector, SelectionKey.OP_WRITE);
      }
   }

   private void mcastInt(int value, int numSlaves) throws ClosedChannelException {
      if (!writeBufferMap.isEmpty()) {
         throw new IllegalStateException("Something not sent to slaves yet: " + writeBufferMap);
      }
      mcastBuffer.clear();
      mcastBuffer.putInt(value);
      for (int i = 0; i < numSlaves; ++i) {
         writeBufferMap.put(slaves[i], ByteBuffer.wrap(mcastBuffer.array(), 0, 4));
         slaves[i].register(communicationSelector, SelectionKey.OP_WRITE);
      }
   }

   @Override
   public List<DistStageAck> runStage(int stageId, int numSlaves) throws IOException {
      responses.clear();
      mcastInt(stageId, numSlaves);
      flushBuffers(numSlaves);
      return Arrays.asList(responses.toArray(new DistStageAck[numSlaves]));
   }

   @Override
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
                  readStageAck(key);
               } else {
                  log.warn("Unknown selection on key " + key);
               }
            }
         }
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

   private void readStageAck(SelectionKey key) throws IOException {
      SocketChannel socketChannel = (SocketChannel) key.channel();

      ByteBuffer byteBuffer = readBufferMap.get(socketChannel);
      int value = socketChannel.read(byteBuffer);

      if (value < 0) {
         Integer slaveIndex = slave2Index.get(socketChannel);
         log.warn("Slave stopped! Index: " + slaveIndex + ". Remote socket is: " + socketChannel);
         key.cancel();
         if (slaveIndex == null || slaves[slaveIndex] != socketChannel) {
            throw new IllegalStateException("Socket " + socketChannel + " should have been there!");
         }
         throw new IOException("Slave stopped");
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
            log.trace("Received response from " + socketChannel);
            Object response = SerializationHelper.deserialize(byteBuffer.array(), 4, expectedSize);
            byteBuffer.clear();
            responses.add(response);
         }
      }
   }

   @Override
   public void release() {
      try {
         mcastObject(null, slaves.length);
         flushBuffers(0);
      } catch (Exception e) {
         log.warn("Failed to send termination to slaves.", e);
      }
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
      ByteBuffer buffer = ByteBuffer.allocate(4);
      while (buffer.hasRemaining()) {
         socketChannel.read(buffer);
      }
      buffer.flip();
      return buffer.getInt();
   }

   private void writeInt(SocketChannel socketChannel, int slaveIndex) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(4);
      buffer.putInt(slaveIndex);
      buffer.flip();
      while (buffer.hasRemaining()) {
         socketChannel.write(buffer);
      }
   }
}
