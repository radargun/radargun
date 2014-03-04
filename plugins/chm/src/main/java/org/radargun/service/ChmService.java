package org.radargun.service;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.radargun.Service;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.ProvidesTrait;

@Service(doc = "ConcurrentHashMap - not a distributed cache.")
public class ChmService {

   protected ConcurrentHashMap<String, ChmCache> caches = new ConcurrentHashMap<String, ChmCache>();

   public ChmService() {
      caches.put(null, new ChmCache());
   }

   public ChmCache getCache(String cacheName) {
      ChmCache cache = caches.get(cacheName);
      if (cache == null) {
         cache = new ChmCache();
         ChmCache prev = caches.putIfAbsent(cacheName, cache);
         if (prev != null) cache = prev;
      }
      return cache;
   }

   @ProvidesTrait
   public BasicOperations createBasicOperations() {
      return new BasicOperations() {
         @Override
         public <K, V> Cache<K, V> getCache(String cacheName) {
            return ChmService.this.getCache(cacheName);
         }
      };
   }

   @ProvidesTrait
   public ConditionalOperations createConditionalOperations() {
      return new ConditionalOperations() {
         @Override
         public <K, V> Cache<K, V> getCache(String cacheName) {
            return ChmService.this.getCache(cacheName);
         }
      };
   }

   @ProvidesTrait
   public CacheInformation createCacheInformation() {
      return new CacheInformation() {
         @Override
         public String getDefaultCacheName() {
            return null;
         }

         @Override
         public Collection<String> getCacheNames() {
            return Collections.unmodifiableSet(caches.keySet());
         }

         @Override
         public Cache getCache(String cacheName) {
            return ChmService.this.getCache(cacheName);
         }
      };
   }
}
