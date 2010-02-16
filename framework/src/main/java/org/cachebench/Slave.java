package org.cachebench;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.state.SlaveState;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Slave being coordinated by a single {@link Master} object in order to run benchmarks.
 *
 * @author Mircea.Markus@jboss.com
 */
public class Slave {

   private static Log log = LogFactory.getLog(Slave.class);

   private String masterHost;
   private boolean exitOnMasterShutdown = true;
   private int masterPort;
   private SocketChannel socketChannel;
   private ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
   private SlaveState state = new SlaveState();

   ExecutorService es = Executors.newSingleThreadExecutor(new ThreadFactory() {
      public Thread newThread(Runnable r) {
         Thread th = new Thread(r);
         th.setDaemon(true);
         return th;
      }
   });
   private Future<?> future;

   public Slave(String masterHost, int masterPort) {
      this.masterHost = masterHost;
      this.masterPort = masterPort;
      Runtime.getRuntime().addShutdownHook(new ShutDownHook("Slave process"));
   }

   private void start() throws Exception {
      connectToMaster();
      startCommunicationWithMaster();
      ShutDownHook.exit(0);
   }

   private void startCommunicationWithMaster() throws Exception {
      Selector selector = Selector.open();
      socketChannel.register(selector, SelectionKey.OP_CONNECT);
      while (true) {
         selector.select();
         // Get set of ready objects
         Set<SelectionKey> readyKeys = selector.selectedKeys();

         Iterator<SelectionKey> readyItor = readyKeys.iterator();
         // Walk through set
         while (readyItor.hasNext()) {
            final SelectionKey key = readyItor.next();
            readyItor.remove();
            SocketChannel keyChannel = (SocketChannel) key.channel();

            if (key.isConnectable()) {
               if (keyChannel.isConnectionPending()) {
                  try {
                     keyChannel.finishConnect();
                  } catch (IOException e) {
                     key.cancel();
                     log.warn("Could not finish connecting. Is the master started?", e);
                     throw e;
                  }
                  state.setLocalAddress(keyChannel.socket().getLocalAddress());
                  state.setMasterAddress(keyChannel.socket().getInetAddress());
                  key.interestOps(SelectionKey.OP_READ);
               }
               log.info("Successfully established connection with master at: " + masterHost + ":" + masterPort);

            } else if (key.isReadable()) {
               int numRead = keyChannel.read(byteBuffer);
               if (numRead == -1) {
                  log.info("Master shutdown!");
                  key.channel().close();
                  key.cancel();
                  return;
               }
               final int expectedSize = byteBuffer.getInt(0);
               if (byteBuffer.position() == expectedSize + 4) {
                  Runnable runnable = new Runnable() {
                     public void run() {
                        try {
                           DistStage stage = (DistStage) SerializationHelper.deserialize(byteBuffer.array(), 4, expectedSize);
                           stage.initOnSlave(state);
                           log.info("Executing stage: " + stage);
                           long start =System.currentTimeMillis();
                           DistStageAck ack = stage.executeOnSlave();
                           ack.setDuration(System.currentTimeMillis() - start);
                           byte[] bytes = SerializationHelper.prepareForSerialization(ack);
                           log.info("Finished stage: " + stage);
                           byteBuffer.clear();
                           byteBuffer.put(bytes);
                           byteBuffer.flip();
                        } catch (IOException e) {
                           log.error(e);
                        }
                     }
                  };
                  future = es.submit(runnable);
                  key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
               }
            } else if (key.isWritable()) {
               try {
                  future.get(500, TimeUnit.MILLISECONDS);
                  if (log.isTraceEnabled()) {
                     log.trace("Buffer before writing is: " + byteBuffer);
                  }
                  key.interestOps(SelectionKey.OP_WRITE);
                  int val = keyChannel.write(byteBuffer);
                  if (log.isTraceEnabled()) {
                     log.trace("Successfully written: " + val + " bytes to the master from buffer: " + byteBuffer);
                  }

                  if (byteBuffer.remaining() == 0) {
                     log.info("Ack successfully sent to the master");
                     byteBuffer.clear();
                     key.interestOps(SelectionKey.OP_READ);
                  }

               } catch (TimeoutException e) {
//                  log.trace("Current stage not finished processing, nothing to write for now.");
               }

            } else {
               log.warn("Received a key that is not connectible, readable or writable: " + key);
            }
         }
      }
   }

   private void connectToMaster() throws IOException {
      InetSocketAddress socketAddress = new InetSocketAddress(masterHost, masterPort);
      log.info("Attempting to connect to master " + masterHost + ":" + masterPort);
      socketChannel = SocketChannel.open();
      socketChannel.configureBlocking(false);
      socketChannel.connect(socketAddress);
      if (exitOnMasterShutdown) {
         es = Executors.newSingleThreadExecutor();
      } else {
         es = new AbstractExecutorService() {
            public void shutdown() {
            }

            public List<Runnable> shutdownNow() {
               return Collections.EMPTY_LIST;
            }

            public boolean isShutdown() {
               return false;
            }

            public boolean isTerminated() {
               return false;
            }

            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
               return false;
            }

            public void execute(Runnable command) {
               command.run();
            }
         };
      }
   }

   public static void main(String[] args) throws Exception {
      String masterHost = null;
      int masterPort = Master.DEFAULT_PORT;
      for (int i = 0; i < args.length - 1; i++) {
         if (args[i].equals("-master")) {
            String param = args[i + 1];
            if (param.contains(":")) {
               masterHost = param.substring(0, param.indexOf(":"));
               try {
                  masterPort = Integer.parseInt(param.substring(param.indexOf(":") + 1));
               } catch (NumberFormatException nfe) {
                  log.warn("Unable to parse port part of the master!  Failing!");
                  ShutDownHook.exit(10);
               }
            } else {
               masterHost = param;
            }
         }
      }
      if (masterHost == null) {
         printUsageAndExit();
      }
      Slave slave = new Slave(masterHost, masterPort);
      slave.start();
   }

   private static void printUsageAndExit() {
      System.out.println("Usage: start_local_slave.sh -master <host>:port");
      System.out.println("       -master: The host(and optional port) on which the master resides. If port is missing it defaults to " + Master.DEFAULT_PORT);
      ShutDownHook.exit(1);
   }
}
