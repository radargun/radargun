package org.radargun.service;

import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.infinispan.AdvancedCache;
import org.infinispan.context.Flag;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InMemoryBasicOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan51Operations extends InfinispanOperations implements InMemoryBasicOperations {
   protected final Infinispan51EmbeddedService service;

   public Infinispan51Operations(Infinispan51EmbeddedService service) {
      super(service);
      this.service = service;
   }

   @Override
   public <K, V> InfinispanCache<K, V> getCache(String cacheName) {
      AdvancedCache<K,V> cache = (AdvancedCache<K,V>) service.getCache(cacheName).getAdvancedCache();
      if (service.isExplicitLocking(cache)) {
         return new ExplicitLockingCache<K, V>(service, cache);
      } else {
         return new Cache<K, V>(service, cache);
      }
   }

   @Override
   public <K, V> BasicOperations.Cache<K, V> getMemoryOnlyCache(String cacheName) {
      AdvancedCache<K, V> cache = (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD, Flag.SKIP_CACHE_STORE);
      return new Cache<K, V>(service, cache);
   }

   protected static class ExplicitLockingCache<K, V> extends InfinispanOperations.Cache<K, V> {
      protected final TransactionManager tm;

      public ExplicitLockingCache(Infinispan51EmbeddedService service, AdvancedCache<K, V> cache) {
         super(service, cache);
         this.tm = cache.getTransactionManager();
      }

      /**
       * If transactions and explicit locking are enabled and the locking mode is pessimistic then
       * explicit locking is performed! i.e. before any put the key is explicitly locked by call
       * cache.lock(key). Doesn't lock the key if the request was made by ClusterValidationStage.
       */
      @Override
      public void put(K key, V value) {
         boolean startedTx = startTxAndLock(key);
         super.put(key, value);
         stopTx(startedTx);
      }

      @Override
      public V getAndPut(K key, V value) {
         boolean startedTx = startTxAndLock(key);
         V prev = super.getAndPut(key, value);
         stopTx(startedTx);
         return prev;
      }

      @Override
      public boolean remove(K key) {
         boolean startedTx = startTxAndLock(key);
         boolean retval = super.remove(key);
         stopTx(startedTx);
         return retval;
      }

      @Override
      public V getAndRemove(K key) {
         boolean startedTx = startTxAndLock(key);
         boolean retval = super.remove(key);
         V prev = super.getAndRemove(key);
         stopTx(startedTx);
         return prev;
      }

      @Override
      public boolean replace(K key, V value) {
         boolean startedTx = startTxAndLock(key);
         boolean retval = super.replace(key, value);
         stopTx(startedTx);
         return retval;
      }

      @Override
      public V getAndReplace(K key, V value) {
         boolean startedTx = startTxAndLock(key);
         V prev = super.getAndReplace(key, value);
         stopTx(startedTx);
         return prev;
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         boolean startedTx = startTxAndLock(key);
         boolean retval = super.replace(key, oldValue, newValue);
         stopTx(startedTx);
         return retval;
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         boolean startedTx = startTxAndLock(key);
         boolean retval = super.putIfAbsent(key, value);
         stopTx(startedTx);
         return retval;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         boolean startedTx = startTxAndLock(key);
         boolean retval = super.remove(key, oldValue);
         stopTx(startedTx);
         return retval;
      }

      private boolean startTxAndLock(K key) {
         boolean startedTx = false;
         try {
            if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
               startedTx = true;
               tm.begin();
            }
            impl.lock(key);
         } catch (Exception e) {
            log.error("Failed to begin transaction and lock", e);
            throw new RuntimeException(e);
         }
         return startedTx;
      }

      private void stopTx(boolean startedTx) {
         if (startedTx) {
            try {
               tm.commit();
            } catch (Exception e) {
               log.error("Failed to commit transaction", e);
            }
         }
      }
   }
}
