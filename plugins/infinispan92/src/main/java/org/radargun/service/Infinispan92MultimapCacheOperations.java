package org.radargun.service;

import java.util.Collection;
import java.util.function.Predicate;

import org.infinispan.multimap.api.embedded.EmbeddedMultimapCacheManagerFactory;
import org.infinispan.multimap.api.embedded.MultimapCacheManager;
import org.radargun.traits.MultimapCacheOperations;

public class Infinispan92MultimapCacheOperations implements MultimapCacheOperations {

   protected final InfinispanEmbeddedService service;
   protected String multimapCacheName;


   public Infinispan92MultimapCacheOperations(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public <K, V> MultimapCache<K, V> getMultimapCache(String cacheName) {
      multimapCacheName = (cacheName == null || cacheName.isEmpty()) ? service.cacheName : cacheName;
      MultimapCacheManager multimapCacheManager = EmbeddedMultimapCacheManagerFactory.from(service.getCacheManager());
      org.infinispan.multimap.api.embedded.MultimapCache<K, V> mmcache = multimapCacheManager.get(multimapCacheName);
      return new MultimapCacheImpl<>(mmcache);
   }

   @Override
   public String getCacheName() {
      return multimapCacheName;
   }

   protected static class MultimapCacheImpl<K, V> implements MultimapCacheOperations.MultimapCache<K, V> {

      protected org.infinispan.multimap.api.embedded.MultimapCache<K, V> multimapCache;

      public MultimapCacheImpl(org.infinispan.multimap.api.embedded.MultimapCache<K, V> multimapCache) {
         this.multimapCache = multimapCache;
      }

      @Override
      public Collection<V> get(K key) throws Exception {
         return multimapCache.get(key).get();
      }

      @Override
      public Void put(K key, V value) throws Exception {
         return multimapCache.put(key, value).get();
      }

      @Override
      public Boolean remove(K key) throws Exception {
         return multimapCache.remove(key).get();
      }

      @Override
      public Boolean remove(K key, V value) throws Exception {
         return multimapCache.remove(key, value).get();
      }

      @Override
      public Void remove(Predicate<? super V> p) throws Exception {
         return multimapCache.remove(p).get();
      }

      @Override
      public Boolean containsKey(K key) throws Exception {
         return multimapCache.containsKey(key).get();
      }

      @Override
      public Boolean containsValue(V value) throws Exception {
         return multimapCache.containsValue(value).get();
      }

      @Override
      public Boolean containsEntry(K key, V value) throws Exception {
         return multimapCache.containsEntry(key, value).get();
      }

      @Override
      public Long size() throws Exception {
         return multimapCache.size().get();
      }
   }
}
