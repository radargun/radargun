package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.infinispan.context.Flag;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.LocalBasicOperations;
import org.radargun.traits.LocalConditionalOperations;
import org.radargun.traits.TemporalOperations;

import java.util.concurrent.TimeUnit;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanOperations implements BasicOperations, ConditionalOperations, LocalBasicOperations, LocalConditionalOperations, TemporalOperations {

   protected final InfinispanEmbeddedService service;

   public InfinispanOperations(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public <K, V> InfinispanCache<K, V> getCache(String cacheName) {
      return new Cache<K, V>(service, (AdvancedCache<K,V>) service.getCache(cacheName).getAdvancedCache());
   }

   @Override
   public <K, V> InfinispanCache<K, V> getLocalCache(String cacheName) {
      return new Cache<K, V>(service, (AdvancedCache<K,V>) service.getCache(cacheName).getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL));
   }

   protected interface InfinispanCache<K, V> extends BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V>, TemporalOperations.Cache<K, V>, AdvancedCacheHolder {}

   protected static class Cache<K, V> implements InfinispanCache<K, V> {
      protected final Log log = LogFactory.getLog(getClass());
      protected final boolean trace = log.isTraceEnabled();

      protected final InfinispanEmbeddedService service;
      protected final org.infinispan.AdvancedCache<K, V> impl;
      protected final org.infinispan.Cache<K, V> ignoreReturnValueImpl;

      public Cache(InfinispanEmbeddedService service, org.infinispan.AdvancedCache<K, V> impl) {
         this.service = service;
         this.impl = impl;
         this.ignoreReturnValueImpl = impl.withFlags(Flag.SKIP_REMOTE_LOOKUP, Flag.SKIP_CACHE_LOAD);
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
         if (trace) log.tracef("PUT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s", impl.getName(), key, value, lifespan);
         ignoreReturnValueImpl.put(key, value, lifespan, TimeUnit.MILLISECONDS);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan) {
         if (trace) log.tracef("GET_AND_PUT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s", impl.getName(), key, value, lifespan);
         return impl.put(key, value, lifespan, TimeUnit.MILLISECONDS);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan) {
         if (trace) log.tracef("PUT_IF_ABSENT_WITH_LIFESPAN cache=%s key=%s value=%s, lifespan=%s", impl.getName(), key, value, lifespan);
         return impl.putIfAbsent(key, value, lifespan, TimeUnit.MILLISECONDS) == null;
      }

      @Override
      public void put(K key, V value, long lifespan, long maxIdleTime) {
         if (trace) log.tracef("PUT_WITH_LIFESPAN_AND_MAXIDLE cache=%s key=%s value=%s lifespan=%s maxIdle=%s", impl.getName(), key, value, lifespan, maxIdleTime);
         ignoreReturnValueImpl.put(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan, long maxIdleTime) {
         if (trace) log.tracef("GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE cache=%s key=%s value=%s lifespan=%s maxIdle=%s", impl.getName(), key, value, lifespan, maxIdleTime);
         return impl.put(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan, long maxIdleTime) {
         if (trace) log.tracef("PUT_IF_ABSENT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s maxIdle=%s", impl.getName(), key, value, lifespan, maxIdleTime);
         return impl.putIfAbsent(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS) == null;
      }
   }
}
