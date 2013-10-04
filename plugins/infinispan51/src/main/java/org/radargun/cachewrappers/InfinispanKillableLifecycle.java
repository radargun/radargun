package org.radargun.cachewrappers;

import java.util.ArrayList;
import java.util.List;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.FD_SOCK;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;
import org.radargun.features.Killable;

/**
 * 
 * InfinispanWrapper that can kill the cache manager by cutting JGroups communication and is able to
 * perform explicit locking.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 * @author Ondrej Nevelik <onevelik@redhat.com>
 * @author Radim Vansa <rvansa@redhat.com>
 */
public class InfinispanKillableLifecycle extends InfinispanLifecycle implements Killable {

   public InfinispanKillableLifecycle(Infinispan51Wrapper wrapper) {
      super(wrapper);
   }

   private enum KillRequest {
      NO_REQUEST,
      KILL_STARTED,
      KILL_FINISHED
   }

   private KillRequest killState = KillRequest.NO_REQUEST;
   private Object killSync = new Object();

   @Override
   public void setUp() throws Exception {
      synchronized (killSync) {
         if (killState == KillRequest.KILL_FINISHED) {
            killState = KillRequest.NO_REQUEST;
         }
      }
      super.setUp();
   }

   @Override
   public void kill() throws Exception {
      startDiscarding();
      try {
         if (beginStop(true)) {
            killInternal();
                    
            stateLock.lock();
            state = State.STOPPED;
         }
      } catch (Exception e) {
         log.error("Wrapper kill failed");
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         throw e;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
         setKillFinished();
      }
   }
   
   @Override
   public void killAsync() throws Exception {
      startDiscarding();
      try {
         if (!beginStop(true)) {
            return;
         }
         new Thread(new Runnable() {
            @Override
            public void run() {
               try {
                  killInternal();

                  stateLock.lock();
                  state = State.STOPPED;
               } catch (Exception e) {
                  log.error("Wrapper async kill failed. Exception while async killing", e);
                  if (!stateLock.isHeldByCurrentThread()) {
                     stateLock.lock();
                  }
                  state = State.FAILED;
               } finally {
                  if (stateLock.isHeldByCurrentThread()) {
                     stateLock.unlock();
                  }
                  setKillFinished();
               }
            }
            }).start();                                
      } catch (Exception e) {
         // only in case of exception, cannot use finally
         setKillFinished();
         throw e;
      }
   }

   private void setKillFinished() {
      synchronized (killSync) {
         if (killState != KillRequest.KILL_STARTED) throw new IllegalStateException();
         killState = KillRequest.KILL_FINISHED;
         killSync.notifyAll();
      }
   }
   
   private void killInternal() throws Exception {
      List<Address> addressList = wrapper.getCacheManager().getMembers();
      wrapper.getCacheManager().stop();
      log.info("Killed, previous view is " + addressList);
   }

   @Override
   protected void postSetUpInternal() throws Exception {
      synchronized (killSync) {
         if (killState == KillRequest.NO_REQUEST) {
            stopDiscarding();
         }
      }
      super.postSetUpInternal();
   }

   protected List<JChannel> getChannels() {
      return getChannels(null, false);
   }
         
   protected List<JChannel> getChannels(JChannel parentChannel, boolean failOnNotReady) {
      List<JChannel> list = new ArrayList<JChannel>();
      if (parentChannel != null) {
         list.add(parentChannel);
         return list;
      }
      JGroupsTransport transport;
      while (wrapper.getCacheManager() == null) {
         notReadyMessage("Cache manager is not ready", failOnNotReady);
         Thread.yield();
      }
      // For local caches it has there is no transport - check that we have at least one clustered cache
      boolean hasClustered = false;
      for (String cacheName : wrapper.getCacheManager().getCacheNames()) {
         if (wrapper.isCacheClustered(wrapper.getCacheManager().getCache(cacheName))) {
            hasClustered = true;
            break;
         }
      }
      if (!hasClustered) return list;
      for (;;) {
         transport = (JGroupsTransport) ((DefaultCacheManager) wrapper.getCacheManager()).getTransport();
         if (transport != null) break;
         notReadyMessage("Transport is not ready", failOnNotReady);
         Thread.yield();
      }
      JChannel channel;
      for(;;) {
         channel = (JChannel) transport.getChannel();
         if (channel != null && channel.getName() != null && channel.isOpen()) break;
         notReadyMessage("Channel " + channel + " is not ready", failOnNotReady);
         Thread.yield();
      }
      list.add(channel);
      return list;
   }

   private void notReadyMessage(String message, boolean failOnNotAvailable) {
      if (failOnNotAvailable) {
         //throw new IllegalStateException(message);
      } else {
         log.trace("Cache Manager is not ready");
      }
   }

   protected void startDiscarding() throws InterruptedException {
      synchronized (killSync) {
         while (killState == KillRequest.KILL_STARTED) killSync.wait();
         killState = KillRequest.KILL_STARTED;
      }
      for (JChannel channel : getChannels()) {
         DISCARD discard = (DISCARD)channel.getProtocolStack().findProtocol(DISCARD.class); 
         if (discard == null) {
            discard = new DISCARD();
            log.debug("No DISCARD protocol in stack for " + channel.getName() + ", inserting new instance");
            try {
               channel.getProtocolStack().insertProtocol(discard, ProtocolStack.ABOVE, TP.class);
            } catch (Exception e) {
               log.error("Failed to insert the DISCARD protocol to stack for " + channel.getName());
               return;
            }         
         }  
         discard.setDiscardAll(true);
         // The FD_SOCK requires special handling because it uses non-standard sockets to interconnect
         FD_SOCK fdSock = (FD_SOCK)channel.getProtocolStack().findProtocol(FD_SOCK.class);
         if (fdSock != null) {
            fdSock.stopServerSocket(false);
         }
      }
      log.debug("Started discarding packets");
   }
   
   protected synchronized void stopDiscarding() {
      if (wrapper.getCacheManager() == null) {
         log.warn("Cache manager is not ready!");
         return;
      }
      for (JChannel channel : getChannels()) {
         DISCARD discard = (DISCARD)channel.getProtocolStack().findProtocol(DISCARD.class); 
         if (discard != null) {
            discard.setDiscardAll(false);            
         } else {
            log.debug("No DISCARD protocol in stack for " + channel.getName());
         }
      }
      log.debug("Stopped discarding.");
   }


}
