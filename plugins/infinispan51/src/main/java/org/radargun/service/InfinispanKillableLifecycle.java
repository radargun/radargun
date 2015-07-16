package org.radargun.service;

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
import org.radargun.traits.Killable;
import org.radargun.utils.TimeService;

/**
 * 
 * InfinispanEmbeddedService that can kill the cache manager by cutting JGroups communication and is able to
 * perform explicit locking.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Ondrej Nevelik &lt;onevelik@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanKillableLifecycle extends InfinispanLifecycle implements Killable {

   protected Infinispan51EmbeddedService service;

   public InfinispanKillableLifecycle(Infinispan51EmbeddedService service) {
      super(service);
      this.service = service;
   }

   private enum KillRequest {
      NO_REQUEST,
      KILL_STARTED,
      KILL_FINISHED
   }

   private KillRequest killState = KillRequest.NO_REQUEST;
   private Object killSync = new Object();

   @Override
   public void start() {
      synchronized (killSync) {
         if (killState == KillRequest.KILL_FINISHED) {
            killState = KillRequest.NO_REQUEST;
         }
      }
      super.start();
   }

   @Override
   protected void afterStopFailed() {
      try {
         log.info("Isolating failed service");
         startDiscarding();
      } catch (Exception e) {
         log.error("Failed to isolate failed service", e);
      } finally {
         setKillFinished();
      }
   }

   @Override
   public void kill() {
      try {
         startDiscarding();
         if (beginStop(true)) {
            killInternal();
                    
            stateLock.lock();
            state = State.STOPPED;
         }
      } catch (Exception e) {
         log.error("Service kill failed");
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         throw new RuntimeException(e);
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
         setKillFinished();
      }
   }
   
   @Override
   public void killAsync() {
      try {
         startDiscarding();
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
                  log.error("Service async kill failed. Exception while async killing", e);
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
         throw new RuntimeException(e);
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
      List<Address> addressList = service.cacheManager.getMembers();
      service.stopCaches();
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
      return getChannels(null);
   }
         
   protected List<JChannel> getChannels(JChannel parentChannel) {
      long deadline = TimeService.currentTimeMillis() + service.channelRetrievalTimeout;
      List<JChannel> list = new ArrayList<JChannel>();
      if (parentChannel != null) {
         list.add(parentChannel);
         return list;
      }
      JGroupsTransport transport;
      while (service.cacheManager == null) {
         if (TimeService.currentTimeMillis() > deadline) {
            return list;
         }
         log.trace("Cache Manager is not ready");
         Thread.yield();
      }
      // For local caches it has there is no transport - check that we have at least one clustered cache
      boolean hasClustered = false;
      for (String cacheName : service.cacheManager.getCacheNames()) {
         if (service.isCacheClustered(service.cacheManager.getCache(cacheName))) {
            hasClustered = true;
            break;
         }
      }
      if (!hasClustered) return list;
      for (;;) {
         transport = (JGroupsTransport) ((DefaultCacheManager) service.cacheManager).getTransport();
         if (transport != null) break;
         if (TimeService.currentTimeMillis() > deadline) {
            return list;
         }
         log.trace("Transport is not ready");
         Thread.yield();
      }
      JChannel channel;
      for(;;) {
         channel = (JChannel) transport.getChannel();
         if (channel != null && channel.getName() != null && channel.isOpen()) break;
         if (TimeService.currentTimeMillis() > deadline) {
            return list;
         }
         log.trace("Channel " + channel + " is not ready");
         Thread.yield();
      }
      list.add(channel);
      return list;
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
         discard.setValue("discard_all", true);
         // The FD_SOCK requires special handling because it uses non-standard sockets to interconnect
         FD_SOCK fdSock = (FD_SOCK)channel.getProtocolStack().findProtocol(FD_SOCK.class);
         if (fdSock != null) {
            fdSock.stopServerSocket(false);
         }
      }
      log.debug("Started discarding packets");
   }
   
   protected synchronized void stopDiscarding() {
      if (service.cacheManager == null) {
         log.warn("Cache manager is not ready!");
         return;
      }
      for (JChannel channel : getChannels()) {
         DISCARD discard = (DISCARD)channel.getProtocolStack().findProtocol(DISCARD.class); 
         if (discard != null) {
            discard.setValue("discard_all", false);
         } else {
            log.debug("No DISCARD protocol in stack for " + channel.getName());
         }
      }
      log.debug("Stopped discarding.");
   }


}
