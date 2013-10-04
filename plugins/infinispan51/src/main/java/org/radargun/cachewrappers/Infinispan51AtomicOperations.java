package org.radargun.cachewrappers;

import javax.transaction.Status;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan51AtomicOperations extends InfinispanAtomicOperations {
   protected final Infinispan51Wrapper wrapper;

   public Infinispan51AtomicOperations(Infinispan51Wrapper wrapper) {
      super(wrapper);
      this.wrapper = wrapper;
   }

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         wrapper.getCache(bucket).getAdvancedCache().lock(key);
      }
      boolean replaced = super.replace(bucket, key, oldValue, newValue);
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
      return replaced;
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         wrapper.getCache(bucket).getAdvancedCache().lock(key);
      }
      Object old = super.putIfAbsent(bucket, key, value);
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
      return old;
   }
}
