package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.infinispan.context.Flag;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.LocalBasicOperations;
import org.radargun.traits.LocalConditionalOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanOperations implements BasicOperations, ConditionalOperations, LocalBasicOperations, LocalConditionalOperations {

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

   protected interface InfinispanCache<K, V> extends BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V> {}

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
         if (trace) log.trace(String.format("PUT cache=%s key=%s value=%s", impl.getName(), key, value));
         ignoreReturnValueImpl.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         if (trace) log.trace(String.format("GET_AND_PUT cache=%s key=%s value=%s", impl.getName(), key, value));
         return impl.put(key, value);
      }

      public V get(K key) {
         if (trace) log.trace(String.format("GET cache=%s key=%s", impl.getName(), key));
         return impl.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         if (trace) log.trace(String.format("CONTAINS cache=%s key=%s", impl.getName(), key));
         return impl.containsKey(key);
      }

      public boolean remove(K key) {
         if (trace) log.trace(String.format("REMOVE cache=%s key=%s", impl.getName(), key));
         return impl.remove(key) != null;
      }

      @Override
      public V getAndRemove(K key) {
         if (trace) log.trace(String.format("GET_AND_REMOVE cache=%s key=%s", impl.getName(), key));
         return impl.remove(key);
      }

      @Override
      public boolean replace(K key, V value) {
         if (trace) log.trace(String.format("REPLACE cache=%s key=%s value=%s", impl.getName(), key, value));
         return impl.replace(key, value) != null;
      }

      @Override
      public V getAndReplace(K key, V value) {
         if (trace) log.trace(String.format("GET_AND_REPLACE cache=%s key=%s value=%s", impl.getName(), key, value));
         return impl.replace(key, value);
      }

      @Override
      public void clear() {
         if (trace) log.trace("CLEAR");
         impl.clear();
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         if (trace) log.trace(String.format("REPLACE cache=%s key=%s old=%s, new=%s", impl.getName(), key, oldValue, newValue));
         return impl.replace(key, oldValue, newValue);
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         if (trace) log.trace(String.format("PUT_IF_ABSENT cache=%s key=%s value=%s", impl.getName(), key, value));
         return impl.putIfAbsent(key, value) == null;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         if (trace) log.trace(String.format("REMOVE cache=%s key=%s value=%s", impl.getName(), key, oldValue));
         return impl.remove(key, oldValue);
      }
   }
}
