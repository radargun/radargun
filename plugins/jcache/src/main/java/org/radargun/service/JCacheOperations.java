package org.radargun.service;

import javax.cache.configuration.MutableConfiguration;

import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;

/**
 * @author Matej Cimbora
 */
public class JCacheOperations implements BasicOperations, ConditionalOperations {

   private JCacheService service;

   public JCacheOperations(JCacheService service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      if (cacheName == null) {
         cacheName = service.cacheName;
      }
      javax.cache.Cache<K, V> cache = service.cacheManager.getCache(cacheName);
      if (cache == null) {
         // TODO allow passing javax.cache configuration properties
         cache = service.cacheManager.createCache(cacheName, new MutableConfiguration<K, V>());
      }
      return new Cache<K, V>(cache);
   }

   private static class Cache<K, V> implements BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V> {

      private javax.cache.Cache<K, V> cache;

      public Cache(javax.cache.Cache<K, V> cache) {
         this.cache = cache;
      }

      @Override
      public V get(K key) {
         return cache.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         return cache.containsKey(key);
      }

      @Override
      public void put(K key, V value) {
         cache.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         return cache.getAndPut(key, value);
      }

      @Override
      public boolean remove(K key) {
         return cache.remove(key);
      }

      @Override
      public V getAndRemove(K key) {
         return cache.getAndRemove(key);
      }

      @Override
      public void clear() {
         cache.clear();
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         return cache.putIfAbsent(key, value);
      }

      @Override
      public boolean remove(K key, V oldValue) {
         return cache.remove(key, oldValue);
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         return cache.replace(key, oldValue, newValue);
      }

      @Override
      public boolean replace(K key, V value) {
         return cache.replace(key, value);
      }

      @Override
      public V getAndReplace(K key, V value) {
         return cache.getAndReplace(key, value);
      }
   }
}
