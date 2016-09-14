package org.radargun.service;

import com.hazelcast.core.IMap;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HazelcastOperations implements BasicOperations, ConditionalOperations {
   protected static final Log log = LogFactory.getLog(HazelcastOperations.class);
   protected static final boolean trace = log.isTraceEnabled();

   protected HazelcastService service = null;

   public HazelcastOperations() {
   }

   public HazelcastOperations(HazelcastService service) {
      this.service = service;
   }

   @Override
   public <K, V> HazelcastCache<K, V> getCache(String cacheName) {
      return new Cache<K, V>(service.<K, V>getMap(cacheName));
   }

   protected interface HazelcastCache<K, V> extends BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V> {}

   protected static class Cache<K, V> implements HazelcastCache<K, V> {
      protected final IMap<K, V> map;

      public Cache(IMap<K, V> map) {
         this.map = map;
      }

      @Override
      public V get(K key) {
         return map.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         return map.containsKey(key);
      }

      @Override
      public void put(K key, V value) {
         map.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         return map.put(key, value);
      }

      @Override
      public boolean remove(K key) {
         return map.remove(key) != null;
      }

      @Override
      public V getAndRemove(K key) {
         return map.remove(key);
      }

      @Override
      public boolean replace(K key, V value) {
         return map.replace(key, value) != null;
      }

      @Override
      public V getAndReplace(K key, V value) {
         return map.replace(key, value);
      }

      @Override
      public void clear() {
         map.clear();
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         return map.putIfAbsent(key, value) == null;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         return map.remove(key, oldValue);
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         return map.replace(key, oldValue, newValue);
      }
   }
}
