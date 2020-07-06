package org.radargun;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.ArgsHolder;

/**
 * Abstracts connection to the main node from worker side.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RemoteMainConnection {
   private static Log log = LogFactory.getLog(RemoteMainConnection.class);

   private String mainHost;
   private int mainPort;
   private SocketChannel socketChannel;
   private ByteBuffer buffer;

   public RemoteMainConnection(String mainHost, int mainPort) {
      this.mainHost = mainHost;
      this.mainPort = mainPort;
      int byteBufferSize = 8192;
      try {
         byteBufferSize = Integer.valueOf(System.getProperty("worker.bufsize", "8192"));
      } catch (Exception e) {
         log.error("Couldn't parse byte buffer size, keeping default", e);
      }
      this.buffer = ByteBuffer.allocate(byteBufferSize);
   }

   /**
    * Connects to the main node, sending requested worker ID.
    *
    * @param workerIndex
    * @return Local address of the worker.
    * @throws IOException
    */
   public InetAddress connectToMain(int workerIndex) throws IOException {
      InetSocketAddress socketAddress = new InetSocketAddress(mainHost, mainPort);
      log.info("Attempting to connect to main " + mainHost + ":" + mainPort);
      for (int i = 0; ; ++i) {
         try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(socketAddress);
            break;
         } catch (IOException e) {
            log.trace("Connect attempt " + i + " failed", e);
            if (i >= 10) {
               throw e;
            }
            try {
               Thread.sleep(2000);
            } catch (InterruptedException interruptedException) {
               Thread.currentThread().interrupt();
               log.warn("Worker thread interrupted", interruptedException);
            }
         }
      }
      log.info("Successfully established connection with main at: " + mainHost + ":" + mainPort);

      buffer.clear();
      buffer.putInt(workerIndex);
      UUID uuid = ArgsHolder.getUuid();
      if (uuid == null) {
         buffer.putLong(0);
         buffer.putLong(0);
      } else {
         buffer.putLong(uuid.getMostSignificantBits());
         buffer.putLong(uuid.getLeastSignificantBits());
      }
      buffer.flip();
      while (buffer.hasRemaining()) socketChannel.write(buffer);
      return socketChannel.socket().getLocalAddress();
   }

   /**
    * Receives final worker ID. Should be called after successful connectToMain() call.
    * @return
    * @throws IOException
    */
   public int receiveWorkerIndex() throws IOException {
      return readInt();
   }

   /**
    * Receives total number of connected workers. Should be called after receiveWorkerIndex().
    * @return
    * @throws IOException
    */
   public int receiveWorkerCount() throws IOException {
      return readInt();
   }

   /**
    * Receive ID of stage that should be now executed. List of stage IDs and configurations
    * was already received as Scenario object.
    * @return
    * @throws IOException
    */
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

   /**
    * Receive any (serializable) object from the main node.
    * @return
    * @throws IOException
    */
   public Object receiveObject() throws IOException {
      // we must expect that more than one object is sent, so read only the first one
      int objectSize = readInt();
      log.trace("Expecting object with size " + objectSize);
      if (objectSize == 0) return null;
      if (objectSize > buffer.capacity()) {
         buffer = ByteBuffer.allocate(objectSize);
      } else {
         buffer.clear();
      }
      buffer.limit(objectSize);
      while (buffer.hasRemaining()) {
         int read = socketChannel.read(buffer);
         log.trace("Read " + read + " bytes");
         if (read < 0) {
            throw new IOException("Cannot read from socket!");
         }
      }
      return SerializationHelper.deserialize(buffer.array(), 0, objectSize);
   }

   /**
    * Send any serializable object to the main node.
    * @param obj
    * @param nextUuid UUID of the next generation of workers, or null if this worker will continue
    * @throws IOException
    */
   public void sendObject(Serializable obj, UUID nextUuid) throws IOException {
      buffer.clear();
      buffer = SerializationHelper.serializeObjectWithLength(obj, buffer);
      if (nextUuid == null) {
         buffer = SerializationHelper.appendLong(0, buffer);
         buffer = SerializationHelper.appendLong(0, buffer);
      } else {
         buffer = SerializationHelper.appendLong(nextUuid.getMostSignificantBits(), buffer);
         buffer = SerializationHelper.appendLong(nextUuid.getLeastSignificantBits(), buffer);
      }
      log.trace("Sending a message to the main, message has " + buffer.position() + " bytes.");
      buffer.flip();
      while (buffer.hasRemaining()) socketChannel.write(buffer);
      log.info("Message successfully sent to the main");
   }

   public void release() throws IOException {
      socketChannel.close();
      socketChannel = null;
   }
}
