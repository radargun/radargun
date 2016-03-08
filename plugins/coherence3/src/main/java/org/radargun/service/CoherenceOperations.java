package org.radargun.service;

import com.tangosol.net.NamedCache;
import com.tangosol.util.extractor.IdentityExtractor;
import com.tangosol.util.filter.EqualsFilter;
import com.tangosol.util.filter.NotFilter;
import com.tangosol.util.filter.PresentFilter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.processors.BooleanConditionalPut;
import org.radargun.processors.BooleanConditionalRemove;
import org.radargun.processors.ValueConditionalPut;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CoherenceOperations implements BasicOperations, ConditionalOperations {
   protected static final Log log = LogFactory.getLog(CoherenceOperations.class);
   protected static final boolean trace = log.isTraceEnabled();

   protected final Coherence3Service service;

   public CoherenceOperations(Coherence3Service service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      return new Cache<K, V>(service.getCache(cacheName));
   }

   protected static class Cache<K, V> implements BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V> {
      protected final NamedCache cache;

      public Cache(NamedCache cache) {
         this.cache = cache;
      }

      @Override
      public V get(K key) {
         if (trace) log.tracef("GET cache=%s key=%s", cache.getCacheName(), key);
         return (V) cache.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         if (trace) log.tracef("CONTAINS cache=%s key=%s", cache.getCacheName(), key);
         return cache.containsKey(key);
      }

      @Override
      public void put(K key, V value) {
         if (trace) log.tracef("PUT cache=%s key=%s value=%s", cache.getCacheName(), key, value);
         // this could be more effective - check it
         // cache.invoke(key, new UpdaterProcessor((ValueUpdater) null, value));
         cache.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         if (trace) log.tracef("GET_AND_PUT cache=%s key=%s value=%s", cache.getCacheName(), key, value);
         return (V) cache.put(key, value);
      }

      @Override
      public boolean remove(K key) {
         if (trace) log.tracef("REMOVE cache=%s key=%s", cache.getCacheName(), key);
         return cache.remove(key) != null;
      }

      @Override
      public V getAndRemove(K key) {
         if (trace) log.tracef("GET_AND_REMOVE cache=%s key=%s", cache.getCacheName(), key);
         return (V) cache.remove(key);
      }

      @Override
      public boolean replace(K key, V value) {
         if (trace) log.tracef("REPLACE cache=%s key=%s value=%s", cache.getCacheName(), key, value);
         return (Boolean) cache.invoke(key, new BooleanConditionalPut(PresentFilter.INSTANCE, value));
      }

      @Override
      public V getAndReplace(K key, V value) {
         if (trace) log.tracef("GET_AND_REPLACE cache=%s key=%s value=%s", cache.getCacheName(), key, value);
         return (V) cache.invoke(key, new ValueConditionalPut(PresentFilter.INSTANCE, value));
      }

      @Override
      public void clear() {
         if (trace) log.trace("CLEAR " + cache.getCacheName());
         cache.clear();
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         if (trace) log.tracef("PUT_IF_ABSENT cache=%s key=%s value=%s", cache.getCacheName(), key, value);
         return (Boolean) cache.invoke(key, new BooleanConditionalPut(new NotFilter(PresentFilter.INSTANCE), value));
      }

      @Override
      public boolean remove(K key, V oldValue) {
         if (trace) log.tracef("REMOVE cache=%s key=%s value=%s", cache.getCacheName(), key, oldValue);
         return (Boolean) cache.invoke(key, new BooleanConditionalRemove(new EqualsFilter(IdentityExtractor.INSTANCE, oldValue)));
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         if (trace) log.tracef("REPLACE cache=%s key=%s old=%s, new=%s", cache.getCacheName(), key, oldValue, newValue);
         return (Boolean) cache.invoke(key, new BooleanConditionalPut(new EqualsFilter(IdentityExtractor.INSTANCE, oldValue), newValue));
      }
   }
}
