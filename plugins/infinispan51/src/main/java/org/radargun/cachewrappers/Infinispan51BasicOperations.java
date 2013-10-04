package org.radargun.cachewrappers;

import javax.transaction.Status;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan51BasicOperations extends InfinispanBasicOperations {
   protected final Infinispan51Wrapper wrapper;

   public Infinispan51BasicOperations(Infinispan51Wrapper wrapper) {
      super(wrapper);
      this.wrapper = wrapper;
   }

   /**
    * If transactions and explicit locking are enabled and the locking mode is pessimistic then
    * explicit locking is performed! i.e. before any put the key is explicitly locked by call
    * cache.lock(key). Doesn't lock the key if the request was made by ClusterValidationStage.
    */
   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         wrapper.getCache(bucket).getAdvancedCache().lock(key);
      }
      super.put(bucket, key, value);
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
   }

   @Override
   public Object remove(String bucket, Object key) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         wrapper.getCache(bucket).getAdvancedCache().lock(key);
      }
      Object old = super.remove(bucket, key);
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
      return old;
   }
}
