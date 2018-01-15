package org.radargun.service;

import java.util.concurrent.TimeUnit;

import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.infinispan.AdvancedCache;
import org.infinispan.context.Flag;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.InMemoryBasicOperations;

public final class Infinispan52Operations
      extends InfinispanOperations implements InMemoryBasicOperations {

   protected final Infinispan52EmbeddedService service52;

   public Infinispan52Operations(Infinispan52EmbeddedService service) {
      super(service);
      this.service52 = service;
   }

   @Override
   public <K, V> Infinispan52Cache<K, V> getCache(String cacheName) {
      if (service.getCache(cacheName) == null) {
         throw new IllegalStateException("Cache named '" + cacheName + "' does not exist");
      }
      AdvancedCache<K, V> impl = (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache();
      Infinispan52Cache<K, V> cache = new Infinispan52CacheImpl<>(service, impl);
      return service52.isExplicitLocking(impl)
            ? new ExplicitLockingCache52<K, V>(cache, impl)
            : cache;
   }

   @Override
   public <K, V> Infinispan52Cache<K, V> getMemoryOnlyCache(String cacheName) {
      if (service.getCache(cacheName) == null) {
         throw new IllegalStateException("Cache named '" + cacheName + "' does not exist");
      }
      AdvancedCache<K, V> cache = (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD, Flag.SKIP_CACHE_STORE);
      return new Infinispan52CacheImpl<K, V>(service, cache);
   }

   @Override
   public <K, V> Infinispan52Cache<K, V> getLocalCache(String cacheName) {
      if (service.getCache(cacheName) == null) {
         throw new IllegalStateException("Cache named '" + cacheName + "' does not exist");
      }
      return new Infinispan52CacheImpl<K, V>(service, (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL));
   }

   private interface Infinispan52Cache<K, V> extends InfinispanCache<K, V> {}

   private static final class Infinispan52CacheImpl<K, V> implements Infinispan52Cache<K, V> {
      protected final Log log = LogFactory.getLog(getClass());
      protected final boolean trace = log.isTraceEnabled();

      protected final InfinispanEmbeddedService service;
      protected final org.infinispan.AdvancedCache<K, V> impl;
      protected final org.infinispan.Cache<K, V> ignoreReturnValueImpl;

      public Infinispan52CacheImpl(InfinispanEmbeddedService service, org.infinispan.AdvancedCache<K, V> impl) {
         this.service = service;
         this.impl = impl;
         this.ignoreReturnValueImpl = impl.withFlags(Flag.IGNORE_RETURN_VALUES);
      }

      public void put(K key, V value) {
         if (trace) log.tracef("PUT cache=%s key=%s value=%s", impl.getName(), key, value);
         ignoreReturnValueImpl.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         if (trace) log.tracef("GET_AND_PUT cache=%s key=%s value=%s", impl.getName(), key, value);
         return impl.put(key, value);
      }

      public V get(K key) {
         if (trace) log.tracef("GET cache=%s key=%s", impl.getName(), key);
         return impl.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         if (trace) log.tracef("CONTAINS cache=%s key=%s", impl.getName(), key);
         return impl.containsKey(key);
      }

      public boolean remove(K key) {
         if (trace) log.tracef("REMOVE cache=%s key=%s", impl.getName(), key);
         return impl.remove(key) != null;
      }

      @Override
      public V getAndRemove(K key) {
         if (trace) log.tracef("GET_AND_REMOVE cache=%s key=%s", impl.getName(), key);
         return impl.remove(key);
      }

      @Override
      public boolean replace(K key, V value) {
         if (trace) log.tracef("REPLACE cache=%s key=%s value=%s", impl.getName(), key, value);
         return impl.replace(key, value) != null;
      }

      @Override
      public V getAndReplace(K key, V value) {
         if (trace) log.tracef("GET_AND_REPLACE cache=%s key=%s value=%s", impl.getName(), key, value);
         return impl.replace(key, value);
      }

      @Override
      public void clear() {
         if (trace) log.trace("CLEAR " + impl.getName());
         impl.clear();
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         if (trace) log.tracef("REPLACE cache=%s key=%s old=%s, new=%s", impl.getName(), key, oldValue, newValue);
         return impl.replace(key, oldValue, newValue);
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         if (trace) log.tracef("PUT_IF_ABSENT cache=%s key=%s value=%s", impl.getName(), key, value);
         return impl.putIfAbsent(key, value) == null;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         if (trace) log.tracef("REMOVE cache=%s key=%s value=%s", impl.getName(), key, oldValue);
         return impl.remove(key, oldValue);
      }

      @Override
      public AdvancedCache getAdvancedCache() {
         return impl;
      }

      @Override
      public void put(K key, V value, long lifespan) {
         if (trace)
            log.tracef("PUT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s", impl.getName(), key, value, lifespan);
         ignoreReturnValueImpl.put(key, value, lifespan, TimeUnit.MILLISECONDS);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan) {
         if (trace)
            log.tracef("GET_AND_PUT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s", impl.getName(), key, value, lifespan);
         return impl.put(key, value, lifespan, TimeUnit.MILLISECONDS);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan) {
         if (trace)
            log.tracef("PUT_IF_ABSENT_WITH_LIFESPAN cache=%s key=%s value=%s, lifespan=%s", impl.getName(), key, value, lifespan);
         return impl.putIfAbsent(key, value, lifespan, TimeUnit.MILLISECONDS) == null;
      }

      @Override
      public void put(K key, V value, long lifespan, long maxIdleTime) {
         if (trace)
            log.tracef("PUT_WITH_LIFESPAN_AND_MAXIDLE cache=%s key=%s value=%s lifespan=%s maxIdle=%s", impl.getName(), key, value, lifespan, maxIdleTime);
         ignoreReturnValueImpl.put(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan, long maxIdleTime) {
         if (trace)
            log.tracef("GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE cache=%s key=%s value=%s lifespan=%s maxIdle=%s", impl.getName(), key, value, lifespan, maxIdleTime);
         return impl.put(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan, long maxIdleTime) {
         if (trace)
            log.tracef("PUT_IF_ABSENT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s maxIdle=%s", impl.getName(), key, value, lifespan, maxIdleTime);
         return impl.putIfAbsent(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS) == null;
      }
   }

   private static final class ExplicitLockingCache52<K, V> implements Infinispan52Cache<K, V> {
      static final Log log = LogFactory.getLog(ExplicitLockingCache52.class);

      final Infinispan52Cache<K, V> delegate;
      final TransactionManager tm;
      final AdvancedCache<K, V> impl;

      public ExplicitLockingCache52(Infinispan52Cache<K, V> delegate, AdvancedCache<K, V> cache) {
         this.delegate = delegate;
         this.impl = cache;
         this.tm = cache.getTransactionManager();
      }

      /**
       * If transactions and explicit locking are enabled and the locking mode is pessimistic then
       * explicit locking is performed! i.e. before any put the key is explicitly locked by call
       * cache.lock(key). Doesn't lock the key if the request was made by ClusterValidationStage.
       */
      @Override
      public void put(K key, V value) {
         boolean startedTx = false;
         try {
            startedTx = startTxAndLock(key);
            delegate.put(key, value);
         } finally {
            stopTx(startedTx);
         }
      }

      @Override
      public V getAndPut(K key, V value) {
         boolean startedTx = false;
         V prev = null;
         try {
            startedTx = startTxAndLock(key);
            prev = delegate.getAndPut(key, value);
         } finally {
            stopTx(startedTx);
         }
         return prev;
      }

      @Override
      public boolean remove(K key) {
         boolean startedTx = false;
         boolean retval = false;
         try {
            startedTx = startTxAndLock(key);
            retval = delegate.remove(key);
         } finally {
            stopTx(startedTx);
         }
         return retval;
      }

      @Override
      public V getAndRemove(K key) {
         boolean startedTx = false;
         V prev = null;
         try {
            startedTx = startTxAndLock(key);
            prev = delegate.getAndRemove(key);
         } finally {
            stopTx(startedTx);
         }
         return prev;
      }

      @Override
      public boolean replace(K key, V value) {
         boolean startedTx = false;
         boolean retval = false;
         try {
            startedTx = startTxAndLock(key);
            retval = delegate.replace(key, value);
         } finally {
            stopTx(startedTx);
         }
         return retval;
      }

      @Override
      public V getAndReplace(K key, V value) {
         boolean startedTx = false;
         V prev = null;
         try {
            startedTx = startTxAndLock(key);
            prev = delegate.getAndReplace(key, value);
         } finally {
            stopTx(startedTx);
         }
         return prev;
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         boolean startedTx = false;
         boolean retval = false;
         try {
            startedTx = startTxAndLock(key);
            retval = delegate.replace(key, oldValue, newValue);
         } finally {
            stopTx(startedTx);
         }
         return retval;
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         boolean startedTx = false;
         boolean retval = false;
         try {
            startedTx = startTxAndLock(key);
            retval = delegate.putIfAbsent(key, value);
         } finally {
            stopTx(startedTx);
         }
         return retval;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         boolean startedTx = false;
         boolean retval = false;
         try {
            startedTx = startTxAndLock(key);
            retval = delegate.remove(key, oldValue);
         } finally {
            stopTx(startedTx);
         }
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

      @Override
      public AdvancedCache getAdvancedCache() {
         return impl;
      }

      @Override
      public V get(K key) {
         return delegate.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         return delegate.containsKey(key);
      }

      @Override
      public void put(K key, V value, long lifespan) {
         delegate.put(key, value, lifespan);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan) {
         return delegate.getAndPut(key, value, lifespan);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan) {
         return delegate.putIfAbsent(key, value, lifespan);
      }

      @Override
      public void put(K key, V value, long lifespan, long maxIdleTime) {
         delegate.put(key, value, lifespan, maxIdleTime);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan, long maxIdleTime) {
         return delegate.getAndPut(key, value, lifespan, maxIdleTime);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan, long maxIdleTime) {
         return delegate.putIfAbsent(key, value, lifespan, maxIdleTime);
      }

      @Override
      public void clear() {
         delegate.clear();
      }

   }

}
