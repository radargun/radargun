package org.cachebench.fwk;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.fwk.state.NodeState;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * /Users/mmarkus/code/benchmarkFwk/cachebenchfwk/framework/src/main/java // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class BenchmarkNode {

   private static Log log = LogFactory.getLog(BenchmarkNode.class);

   private String serverHost;
   private int serverPort;
   private SocketChannel socketChannel;
   private ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
   private NodeState state = new NodeState();

   public BenchmarkNode(String serverHost, int serverPort) {
      this.serverHost = serverHost;
      this.serverPort = serverPort;
   }

   private void start() throws IOException {
      connectToServer();
      startCommunicationWithServer();
   }

   private void startCommunicationWithServer() throws IOException {
      Selector selector = Selector.open();
      socketChannel.register(selector, SelectionKey.OP_CONNECT);
      while (true) {
         selector.select();
         // Get set of ready objects
         Set<SelectionKey> readyKeys = selector.selectedKeys();
         if (log.isTraceEnabled())
            log.trace("Received " + readyKeys.size() + " key(s). ");

         Iterator<SelectionKey> readyItor = readyKeys.iterator();
         // Walk through set
         while (readyItor.hasNext()) {
            SelectionKey key = readyItor.next();
            readyItor.remove();
            SocketChannel keyChannel = (SocketChannel) key.channel();

            if (key.isConnectable()) {
               if (keyChannel.isConnectionPending()) {
                  try {
                     keyChannel.finishConnect();
                  } catch (IOException e) {
                     key.cancel();
                     log.warn("Could not finish connecting. Is the server started?", e);
                     throw e;
                  }
                  state.setLocalAddress(keyChannel.socket().getLocalAddress());
                  state.setServerAddress(keyChannel.socket().getInetAddress());
                  key.interestOps(SelectionKey.OP_READ);
               }
               log.info("Successfully established connection with server at: " + serverHost + ":" + serverPort);

            } else if (key.isReadable()) {
               int numRead = keyChannel.read(byteBuffer);
               if (numRead == -1) {
                  // Remote entity shut the socket down cleanly. Do the
                  // same from our end and cancel the channel.
                  key.channel().close();
                  key.cancel();
                  return;
               }
               int expectedSize = byteBuffer.getInt(0);
               if (byteBuffer.position() == expectedSize + 4) {
                  DistStage stage = (DistStage) SerializationHelper.deserialize(byteBuffer.array(), 4, expectedSize);
                  stage.initOnNode(state);
                  log.trace("Received stage from server " + stage);
                  DistStageAck ack = stage.executeOnNode();
                  byte[] bytes = SerializationHelper.prepareForSerialization(ack);
                  byteBuffer.clear();
                  byteBuffer.put(bytes);
                  byteBuffer.flip();
                  key.interestOps(SelectionKey.OP_WRITE);
               }
            } else if (key.isWritable()) {
               keyChannel.write(byteBuffer);
               if (byteBuffer.remaining() == 0) {
                  log.info("Ack successfully sent to the server");
                  byteBuffer.clear();
                  key.interestOps(SelectionKey.OP_READ);
               }
            } else {
               log.warn("Received a key that is not connectable, readable or writable: " + key);
            }
         }
      }
   }

   private void connectToServer() throws IOException {
      InetSocketAddress socketAddress = new InetSocketAddress(serverHost, serverPort);
      log.info("Attempting to connect to master server " + serverHost + ":" + serverPort);
      socketChannel = SocketChannel.open();
      socketChannel.configureBlocking(false);
      socketChannel.connect(socketAddress);
   }

   public static void main(String[] args) throws Exception {
      String serverHost = null;
      int serverPort = BenchmarkServer.DEFAULT_PORT;
      for (int i = 0; i < args.length - 1; i++) {
         if (args[i].equals("-serverHost")) {
            String param = args[i + 1];
            if (param.contains(":")) {
               serverHost = param.substring(0, param.indexOf(":"));
               try {
                  serverPort = Integer.parseInt(param.substring(param.indexOf(":") + 1));
               } catch (NumberFormatException nfe) {
                  log.warn("Unable to parse port part of the master server!  Failing!");
                  System.exit(10);
               }
            } else {
               serverHost = param;
            }
         } else if (args[i].equals("-serverPort")) {
            try {
               serverPort = Integer.parseInt(args[i + 1]);
            } catch (NumberFormatException e) {
               System.err.println("Incorrect server port: " + args[i + 1]);
               printUsageAndExit();
            }
         } else {
            System.out.println("Unknown parameter: " + args[i]);
         }
      }
      if (serverHost == null) {
         printUsageAndExit();
      }
      BenchmarkNode benchmarkNode = new BenchmarkNode(serverHost, serverPort);
      benchmarkNode.start();
   }

   private static void printUsageAndExit() {
      System.out.println("Usage: start_local_slave.sh [-serverHost <host>] [-serverPort <port>]");
      System.out.println("       -serverHost: The host on which the server resides. Optional.");
      System.out.println("       -serverPort: The port on which the server is listening for connections. Optional, defaults to " + BenchmarkServer.DEFAULT_PORT);
      System.exit(1);
   }
}
