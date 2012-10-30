package org.radargun.cachewrappers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.DataRehashed;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.DataRehashedEvent;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.jgroups.JChannel;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.FD_SOCK;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;
import org.radargun.features.Killable;
import org.radargun.features.TopologyAware;
import org.radargun.utils.TypedProperties;

/**
 * 
 * InfinispanWrapper that can kill the cache manager by cutting JGroups communication and is able to
 * perform explicit locking.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 * @author Ondrej Nevelik <onevelik@redhat.com>
 * @author Radim Vansa <rvansa@redhat.com>
 */
public class InfinispanKillableWrapper extends InfinispanExplicitLockingWrapper implements Killable, TopologyAware {

   private static Log log = LogFactory.getLog(InfinispanKillableWrapper.class);

   private enum KillRequest {
      NO_REQUEST,
      KILL_STARTED,
      KILL_FINISHED
   }

   private List<TopologyAware.Event> topologyChanges = new ArrayList<TopologyAware.Event>();
   private List<TopologyAware.Event> hashChanges = new ArrayList<TopologyAware.Event>();
   private KillRequest killState = KillRequest.NO_REQUEST;
   private Object killSync = new Object();

   @Override
   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      synchronized (killSync) {
         if (killState == KillRequest.KILL_FINISHED) {
            killState = KillRequest.NO_REQUEST;
         }
      }
      super.setUp(config, isLocal, nodeIndex, confAttributes);
   }

   @Override
   public void kill() throws Exception {
      startDiscarding();
      try {
         if (beginStop()) {
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
         if (!beginStop()) {
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
      List<Address> addressList = cacheManager.getMembers();
      cacheManager.stop();
      log.info("Killed, previous view is " + addressList);
   }

   @Override
   protected void postSetUpInternal(TypedProperties confAttributes) throws Exception {      
      synchronized (killSync) {
         if (killState == KillRequest.NO_REQUEST) {
            stopDiscarding();
         }
      }
      super.postSetUpInternal(confAttributes);
      getCache(null).addListener(new TopologyAwareListener());
   }
         
   protected List<JChannel> getChannels() {
      List<JChannel> list = new ArrayList<JChannel>();
      JGroupsTransport transport;
      while (cacheManager == null) {
         log.trace("Cache Manager is not ready");
         Thread.yield();
      }
      for (;;) {
         transport = (JGroupsTransport) ((DefaultCacheManager) cacheManager).getTransport();
         if (transport != null) break;
         log.trace("Transport is not ready");
         Thread.yield();
      }
      JChannel channel;
      for(;;) {
         channel = (JChannel) transport.getChannel();
         if (channel != null && channel.getName() != null && channel.isOpen()) break;
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
      if (cacheManager == null) {
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

   @Override
   public List<TopologyAware.Event> getTopologyChangeHistory() {
      return Collections.unmodifiableList(topologyChanges);
   }

   @Override
   public List<TopologyAware.Event> getRehashHistory() {
      return Collections.unmodifiableList(hashChanges);
   }

   @Override
   public boolean isCoordinator() {
      return ((DefaultCacheManager) cacheManager).isCoordinator();
   }

   @Listener
   public class TopologyAwareListener {
      @TopologyChanged
      public void onTopologyChanged(TopologyChangedEvent<?,?> e) {
         log.debug("Topology change " + (e.isPre() ? "started" : "finished"));
         addEvent(topologyChanges, e.isPre());
      }
      
      @DataRehashed
      public void onDataRehashed(DataRehashedEvent<?,?> e) {
         log.debug("Rehash " + (e.isPre() ? "started" : "finished"));
         addEvent(hashChanges, e.isPre());
      }

      private void addEvent(List<TopologyAware.Event> list, boolean isPre) {
         if (isPre) {
            list.add(new Event(false));
         } else {
            int size = list.size();
            if (size == 0 || list.get(size - 1).getEnded() != null) {
               Event ev = new Event(true);                  
               list.add(ev);
            } else {
               ((Event) list.get(size - 1)).setEnded();
            }
         }
      }
      
      class Event extends TopologyAware.Event {
         private Date started;
         private Date ended;
         
         public Event(boolean finished) {
            if (finished) {
               this.started = this.ended = new Date();
            } else {
               this.started = new Date();
            }
         }

         @Override
         public Date getStarted() {
            return started;
         }
         
         public void setEnded() {
            if (ended != null) throw new IllegalStateException();
            ended = new Date();
         }

         @Override
         public Date getEnded() {
            return ended;
         }      
      }
   }
}
