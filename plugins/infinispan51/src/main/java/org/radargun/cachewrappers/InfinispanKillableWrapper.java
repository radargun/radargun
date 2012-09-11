package org.radargun.cachewrappers;

import javax.transaction.Status;

import org.infinispan.Cache;
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
      if (started) {
         JGroupsTransport transport = (JGroupsTransport) cacheManager.getTransport();
         JChannel channel = (JChannel) transport.getChannel();
         DISCARD discard = (DISCARD)channel.getProtocolStack().findProtocol(DISCARD.class); 
         if (discard == null) {
            discard = new DISCARD();                        
            channel.getProtocolStack().insertProtocol(discard, ProtocolStack.ABOVE, TP.class);
         }  
         // Disabling the discard as ISPN cannot handle it gracefully, see https://issues.jboss.org/browse/ISPN-2283
         // discard.setDiscardAll(true);
         cacheManager.stop();
         started = false;
      }
   }

   @Override
   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes)
            throws Exception {
      super.setUp(config, isLocal, nodeIndex, confAttributes);
      
      stopDiscarding();
      
      setUpExplicitLocking(getCache(), confAttributes);
   }
   
   protected void stopDiscarding() {
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

   protected boolean isClusterValidationRequest(String bucket) {
      return bucket.startsWith("clusterValidation") ? true : false;
   }

   public boolean isExplicitLockingEnabled() {
      return isExplicitLocking;
   }

}
