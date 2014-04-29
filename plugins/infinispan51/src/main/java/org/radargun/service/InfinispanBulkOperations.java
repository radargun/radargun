package org.radargun.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.infinispan.AdvancedCache;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BulkOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanBulkOperations implements BulkOperations {
   protected final Log log = LogFactory.getLog(InfinispanBulkOperations.class);
   protected final boolean trace = log.isTraceEnabled();

   protected final Infinispan51EmbeddedService service;

   public InfinispanBulkOperations(Infinispan51EmbeddedService service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName, boolean preferAsync) {
      return new Cache<K, V>((AdvancedCache<K,V>) service.getCache(cacheName).getAdvancedCache(), preferAsync);
   }

   protected class Cache<K, V> implements BulkOperations.Cache<K, V> {

      protected final AdvancedCache<K, V> impl;
      protected final TransactionManager tm;
      protected final boolean preferAsync;

      public Cache(AdvancedCache<K, V> cache, boolean preferAsync) {
         this.impl = cache;
         this.preferAsync = preferAsync;
         this.tm = cache.getTransactionManager();
      }

      @Override
      public Map<K, V> getAll(Set<K> keys) {
         if (trace) {
            StringBuilder sb = new StringBuilder("GET_ALL ");
            for (K key : keys) {
               sb.append(key).append(", ");
            }
            log.trace(sb.toString());
         }
         Map<K, Future<V>> futures = new HashMap<K, Future<V>>(keys.size());
         Map<K, V> values = new HashMap<K, V>(keys.size());
         for (K key : keys) {
            futures.put(key, impl.getAsync(key));
         }
         for (Map.Entry<K, Future<V>> entry : futures.entrySet()) {
            try {
               V value = entry.getValue().get();
               if (value != null) {
                  values.put(entry.getKey(), value);
               }
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }
         return values;
      }

      @Override
      public void putAll(Map<K, V> entries) {
         if (trace) {
            StringBuilder sb = new StringBuilder("PUT_ALL ");
            for (Object key : entries.keySet()) {
               sb.append(key).append(", ");
            }
            log.trace(sb.toString());
         }
         boolean startedTx = startTxAndLock(entries.keySet());
         if (preferAsync) {
            Map<K, Future<V>> futures = new HashMap<K, Future<V>>(entries.size());
            for (Map.Entry<K, V> entry : entries.entrySet()) {
               futures.put(entry.getKey(), impl.putAsync(entry.getKey(), entry.getValue()));
            }
            for (Map.Entry<K, Future<V>> entry : futures.entrySet()) {
               try {
                  entry.getValue().get();
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            }
         } else {
            impl.putAll(entries);
         }
         stopTx(startedTx);
      }

      @Override
      public void removeAll(Set<K> keys) {
         if (trace) {
            StringBuilder sb = new StringBuilder("REMOVE_ALL ");
            for (Object key : keys) {
               sb.append(key).append(", ");
            }
            log.trace(sb.toString());
         }
         boolean startedTx = startTxAndLock(keys);
         Map<K, Future<V>> futures = new HashMap<K, Future<V>>(keys.size());
         for (K key : keys) {
            futures.put(key, impl.removeAsync(key));
         }
         for (Map.Entry<K, Future<V>> entry : futures.entrySet()) {
            try {
               entry.getValue().get();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }
         stopTx(startedTx);
      }

      private boolean startTxAndLock(Set<K> keys) {
         boolean shouldStopTransactionHere = false;
         try {
            if (service.isExplicitLocking(impl)) {
               if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
                  shouldStopTransactionHere = true;
                  tm.begin();
               }
               impl.lock(keys);
            }
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         return shouldStopTransactionHere;
      }

      private void stopTx(boolean startedTx) {
         if (startedTx) {
            try {
               tm.commit();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }
      }
   }
}
