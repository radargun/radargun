package org.radargun.cachewrappers;

import javax.transaction.Status;
import java.util.HashMap;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.context.Flag;
import org.radargun.features.PersistentStorageCapable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanPersistentStorage implements PersistentStorageCapable {
   protected Infinispan51Wrapper wrapper;
   protected Map<String, AdvancedCache> storages = new HashMap<String, AdvancedCache>();

   public InfinispanPersistentStorage(Infinispan51Wrapper wrapper) {
      this.wrapper = wrapper;
      for (String bucket : wrapper.getBuckets()) {
         storages.put(bucket, wrapper.getCache(bucket).getAdvancedCache()
               .withFlags(Flag.SKIP_CACHE_LOAD, Flag.SKIP_CACHE_STORE));
      }
   }

   @Override
   public Object getMemoryOnly(String bucket, Object key) throws Exception {
      return storages.get(bucket).get(key);
   }

   @Override
   public Object putMemoryOnly(String bucket, Object key, Object value) throws Exception {
      boolean shouldStopTransactionHere = false;
      AdvancedCache storage = storages.get(bucket);
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         storage.lock(key);
      }
      Object retval = storage.put(key, value);
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
      return retval;
   }

   @Override
   public Object removeMemoryOnly(String bucket, Object key) throws Exception {
      boolean shouldStopTransactionHere = false;
      AdvancedCache storage = storages.get(bucket);
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         storage.lock(key);
      }
      Object retval = storage.remove(key);
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
      return retval;
   }
}
