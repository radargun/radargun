package org.radargun.cachewrappers;

import javax.transaction.Status;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.infinispan.Cache;
import org.radargun.features.BulkOperationsCapable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanBulkOperations implements BulkOperationsCapable {
   protected final Log log = LogFactory.getLog(InfinispanBulkOperations.class);
   protected final boolean trace = log.isTraceEnabled();

   protected final Infinispan51Wrapper wrapper;

   public InfinispanBulkOperations(Infinispan51Wrapper wrapper) {
      this.wrapper = wrapper;
   }

   @Override
   public Map<Object, Object> getAll(String bucket, Set<Object> keys, boolean preferAsyncOperations) throws Exception {
      if (trace) {
         StringBuilder sb = new StringBuilder("GET_ALL ");
         for (Object key : keys) {
            sb.append(key).append(", ");
         }
      }
      Cache<Object, Object> cache = wrapper.getCache(bucket);
      Map<Object, Future<Object>> futures = new HashMap<Object, Future<Object>>(keys.size());
      Map<Object, Object> values = new HashMap<Object, Object>(keys.size());
      for (Object key : keys) {
         futures.put(key, cache.getAsync(key));
      }
      for (Map.Entry<Object, Future<Object>> entry : futures.entrySet()) {
         values.put(entry.getKey(), entry.getValue().get());
      }
      return values;
   }

   @Override
   public Map<Object, Object> putAll(String bucket, Map<Object, Object> entries, boolean preferAsyncOperations) throws Exception {
      if (trace) {
         StringBuilder sb = new StringBuilder("PUT_ALL ");
         for (Object key : entries.keySet()) {
            sb.append(key).append(", ");
         }
      }
      Cache<Object, Object> cache = wrapper.getCache(bucket);
      boolean shouldStopTransactionHere = false;
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         cache.getAdvancedCache().lock(entries.keySet());
      }
      Map<Object, Object> values;
      if (preferAsyncOperations) {
         Map<Object, Future<Object>> futures = new HashMap<Object, Future<Object>>(entries.size());
         values = new HashMap<Object, Object>();
         for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            futures.put(entry.getKey(), cache.putAsync(entry.getKey(), entry.getValue()));
         }
         for (Map.Entry<Object, Future<Object>> entry : futures.entrySet()) {
            values.put(entry.getKey(), entry.getValue().get());
         }
      } else {
         cache.putAll(entries);
         values = null;
      }
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
      return values;
   }

   @Override
   public Map<Object, Object> removeAll(String bucket, Set<Object> keys, boolean preferAsyncOperations) throws Exception {
      if (trace) {
         StringBuilder sb = new StringBuilder("GET_ALL ");
         for (Object key : keys) {
            sb.append(key).append(", ");
         }
      }
      Cache<Object, Object> cache = wrapper.getCache(bucket);
      boolean shouldStopTransactionHere = false;
      if (wrapper.isExplicitLocking() && !wrapper.isClusterValidationRequest(bucket)) {
         if (wrapper.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            wrapper.startTransaction();
         }
         cache.getAdvancedCache().lock(keys);
      }
      Map<Object, Future<Object>> futures = new HashMap<Object, Future<Object>>(keys.size());
      Map<Object, Object> values = new HashMap<Object, Object>();
      for (Object key : keys) {
         futures.put(key, cache.removeAsync(key));
      }
      for (Map.Entry<Object, Future<Object>> entry : futures.entrySet()) {
         values.put(entry.getKey(), entry.getValue().get());
      }
      if (shouldStopTransactionHere) {
         wrapper.endTransaction(true);
      }
      return values;
   }
}
