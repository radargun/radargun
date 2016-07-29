package org.radargun.service;

import com.hazelcast.core.BaseMap;
import com.hazelcast.core.IMap;
import com.hazelcast.core.TransactionalMap;

/**
 * Functionally same as {@link HazelcastOperations} but the interfaces have changed a bit
 * and in order to support transactions we have to adapt.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Hazelcast36Operations extends HazelcastOperations {

   public Hazelcast36Operations(Hazelcast36Service service) {
      super(service);
   }

   @Override
   public <K, V> HazelcastCache<K, V> getCache(String cacheName) {
      return new Cache<K, V>(service.<K, V>getMap(cacheName));
   }

   protected static class Cache<K, V> implements HazelcastCache<K, V> {
      protected final BaseMap<K, V> map;
      protected final IMap<K, V> clearMap;

      public Cache(IMap<K, V> map) {
         this.map = map;
         this.clearMap = map;
      }

      public Cache(TransactionalMap<K, V> map) {
         this.map = map;
         this.clearMap = null;
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
         if (clearMap == null) {
            throw new UnsupportedOperationException("Cannot clear in transaction.");
         } else {
            clearMap.clear();
         }
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
