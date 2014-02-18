package org.radargun;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RemoteMasterConnection {
   private static Log log = LogFactory.getLog(RemoteMasterConnection.class);

   private String masterHost;
   private int masterPort;
   private SocketChannel socketChannel;
   private ByteBuffer buffer;

   public RemoteMasterConnection(String masterHost, int masterPort) {
      this.masterHost = masterHost;
      this.masterPort = masterPort;
      int byteBufferSize = 8192;
      try {
         byteBufferSize = Integer.valueOf(System.getProperty("slave.bufsize", "8192"));
      } catch (Exception e) {
         log.error("Couldn't parse byte buffer size, keeping default", e);
      }
      this.buffer = ByteBuffer.allocate(byteBufferSize);
   }

   public InetAddress connectToMaster(int slaveIndex) throws IOException {
      InetSocketAddress socketAddress = new InetSocketAddress(masterHost, masterPort);
      log.info("Attempting to connect to master " + masterHost + ":" + masterPort);
      socketChannel = SocketChannel.open();
      socketChannel.connect(socketAddress);
      log.info("Successfully established connection with master at: " + masterHost + ":" + masterPort);

      writeInt(slaveIndex);
      return socketChannel.socket().getLocalAddress();
   }

   private void writeInt(int value) throws IOException {
      buffer.clear();
      buffer.putInt(value);
      buffer.flip();
      while (buffer.hasRemaining()) socketChannel.write(buffer);
   }

   public int receiveSlaveIndex() throws IOException {
      return readInt();
   }

   public int receiveSlaveCount() throws IOException {
      return readInt();
   }

   public int receiveNextStageId() throws IOException {
      return readInt();
   }

   private int readInt() throws IOException {
      buffer.clear();
      buffer.limit(4);
      while (buffer.hasRemaining()) {
         int read = socketChannel.read(buffer);
         if (read < 0) {
            throw new IOException("Cannot read from socket!");
         }
      }
      buffer.flip();
      return buffer.getInt();
   }

   public Object receiveObject() throws IOException {
      // we must expect that more than one object is sent, so read only the first one
      int objectSize = readInt();
      if (objectSize == 0) return null;
      if (objectSize > buffer.capacity()) {
         buffer = ByteBuffer.allocate(objectSize);
      } else {
         buffer.clear();
      }
      buffer.limit(objectSize);
      while (buffer.hasRemaining()) {
         int read = socketChannel.read(buffer);
         if (read < 0) {
            throw new IOException("Cannot read from socket!");
         }
      }
      return SerializationHelper.deserialize(buffer.array(), 0, objectSize);
   }

   public void sendReponse(DistStageAck response) throws IOException {
      buffer.clear();
      buffer = SerializationHelper.serializeObjectWithLength(response, buffer);
      log.trace("Sending response to the master, response has " + buffer.position() + " bytes.");
      buffer.flip();
      while (buffer.hasRemaining()) socketChannel.write(buffer);
      log.info("Response successfully sent to the master");
   }
}
