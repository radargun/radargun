package org.radargun.service;

import java.util.Map;

import org.jboss.cache.util.Caches;
import org.radargun.traits.BasicOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JBossCache3Operations extends JBossCache2Operations {
   protected final boolean flatCache;

   public JBossCache3Operations(JBossCache3Service service, boolean flatCache) {
      super(service);
      this.flatCache = flatCache;
   }

   @Override
   public <K, V> BasicOperations.Cache<K, V> getCache(String cacheName) {
      if (flatCache) {
         return new FlatCache<K, V>(Caches.asMap(service.getCache(cacheName)));
      } else {
         return super.getCache(cacheName);
      }
   }

   protected class FlatCache<K, V> implements BasicOperations.Cache<K, V> {
      private final Map<K, V> map;

      public FlatCache(Map<K, V> map) {
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
      public void clear() {
         map.clear();
      }
   }
}
