package org.radargun.cachewrappers;

import java.util.List;

import javax.transaction.Status;

import org.infinispan.Cache;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.infinispan.transaction.LockingMode;
import org.jgroups.JChannel;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.TP;
import org.jgroups.stack.ProtocolStack;
import org.radargun.Killable;
import org.radargun.utils.TypedProperties;

/**
 * 
 * InfinispanWrapper that can kill the cache manager by cutting JGroups communication and is able to
 * perform explicit locking.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 * @author Ondrej Nevelik <onevelik@redhat.com>
 */
public class InfinispanKillableWrapper extends InfinispanWrapper implements Killable {

   private boolean isExplicitLocking;
   private static Log log = LogFactory.getLog(InfinispanKillableWrapper.class);
   
   @Override
   public void kill() throws Exception {
      try {
         stateLock.lock();
         while (state == State.STARTING) {
            stateLock.unlock();
            log.info("Waiting for the wrapper to start");
            Thread.sleep(5000);
            stateLock.lock();
         }
         if (state == State.STOPPING) {
            log.warn("Wrapper already stopping");
         } else if (state == State.STOPPED) {
            log.warn("Wrapper already stopped");
         } else if (state == State.STARTED) {
            state = State.STOPPING;
            stateLock.unlock();
            
            killInternal();
                    
            stateLock.lock();
            state = State.STOPPED;
         }
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }
   
   @Override
   public void killAsync() throws Exception {
      try {
         stateLock.lock();
         while (state == State.STARTING) {
            stateLock.unlock();
            log.info("Waiting for the wrapper to start");
            Thread.sleep(5000);
            stateLock.lock();
         }
         if (state == State.STOPPING) {
            log.warn("Wrapper already stopping");
         } else if (state == State.STOPPED) {
            log.warn("Wrapper already stopped");
         } else if (state == State.STARTED) {
            state = State.STOPPING;
            stateLock.unlock();
            
            new Thread(new Runnable() {
               @Override
               public void run() {
                  try {
                     killInternal();
                     
                     stateLock.lock();
                     state = State.STOPPED;
                  } catch (Exception e) {
                     log.error("Exception while async killing", e);
                  } finally {
                     if (stateLock.isHeldByCurrentThread()) {
                        stateLock.unlock();
                     }
                  }
               }
            }).start();                                
         }
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }   
   }
   
   private void killInternal() throws Exception {
      JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
      JChannel channel = (JChannel) transport.getChannel();
      DISCARD discard = (DISCARD)channel.getProtocolStack().findProtocol(DISCARD.class); 
      if (discard == null) {
         discard = new DISCARD();                        
         channel.getProtocolStack().insertProtocol(discard, ProtocolStack.ABOVE, TP.class);
      }  
      discard.setDiscardAll(true);
      List<Address> addressList = cacheManager.getMembers();
      cacheManager.stop();
      log.info("Killed, previous view is " + addressList);
   }

   @Override
   protected void postSetUpInternal(TypedProperties confAttributes) throws Exception {      
      stopDiscarding();
      super.postSetUpInternal(confAttributes);      
      setUpExplicitLocking(getCache(), confAttributes);
   }
   
   protected void stopDiscarding() {
      if (cacheManager == null) {
         log.warn("Cache manager is not ready!");
         return;
      }
      JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
      JChannel channel = (JChannel) transport.getChannel();
      DISCARD discard = (DISCARD)channel.getProtocolStack().findProtocol(DISCARD.class); 
      if (discard != null) {
         discard.setDiscardAll(false);
      }
   }

   protected void setUpExplicitLocking(Cache aCache, TypedProperties confAttributes) {
      LockingMode lockingMode = aCache.getAdvancedCache().getCacheConfiguration().transaction()
               .lockingMode();

      Object explicitLocking = confAttributes.get("explicitLocking");
      if (explicitLocking != null && explicitLocking.equals("true")
               && lockingMode.equals(LockingMode.PESSIMISTIC)) {
         isExplicitLocking = true;
         log.info("Using explicit locking!");
      }
   }

   /**
    * If transactions and explicit locking are enabled and the locking mode is pessimistic then
    * explicit locking is performed! i.e. before any put the key is explicitly locked by call
    * cache.lock(key). Doesn't lock the key if the request was made by ClusterValidationStage.
    */
   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         getCache().getAdvancedCache().lock(key);
      }
      super.put(bucket, key, value);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
   }
   
   @Override
   public Object remove(String bucket, Object key) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (isExplicitLocking && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         getCache().getAdvancedCache().lock(key);
      }
      Object old = super.remove(bucket, key);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
      return old;
   }

   protected boolean isClusterValidationRequest(String bucket) {
      return bucket.startsWith("clusterValidation") ? true : false;
   }

   public boolean isExplicitLockingEnabled() {
      return isExplicitLocking;
   }

}
