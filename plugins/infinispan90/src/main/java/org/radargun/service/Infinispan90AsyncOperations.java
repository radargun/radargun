package org.radargun.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.api.AsyncCache;
import org.infinispan.context.Flag;
import org.radargun.traits.BasicAsyncOperations;
import org.radargun.traits.ConditionalAsyncOperations;

public class Infinispan90AsyncOperations implements BasicAsyncOperations, ConditionalAsyncOperations {
   protected final Infinispan90EmbeddedService service;

   public Infinispan90AsyncOperations(Infinispan90EmbeddedService service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      AdvancedCache cache = service.getCache(cacheName).getAdvancedCache();
      return new Cache(cache, cache.withFlags(Flag.IGNORE_RETURN_VALUES));
   }

   protected static class Cache<K, V> implements BasicAsyncOperations.Cache<K, V>, ConditionalAsyncOperations.Cache<K, V> {
      private final AsyncCache<K, V> cache;
      private final AsyncCache<K, V> noReturnCache;

      public Cache(AsyncCache<K, V> cache, AsyncCache<K, V> noReturnCache) {
         this.cache = cache;
         this.noReturnCache = noReturnCache;
      }

      @Override
      public CompletableFuture<V> get(K key) {
         return cache.getAsync(key);
      }

      @Override
      public CompletableFuture<Boolean> containsKey(K key) {
         return cache.getAsync(key).thenApply(Objects::nonNull);
      }

      @Override
      public CompletableFuture<Void> put(K key, V value) {
         return noReturnCache.putAsync(key, value).thenApply(nil -> null);
      }

      @Override
      public CompletableFuture<V> getAndPut(K key, V value) {
         return cache.putAsync(key, value);
      }

      @Override
      public CompletableFuture<Boolean> remove(K key) {
         return cache.removeAsync(key).thenApply(Objects::nonNull);
      }

      @Override
      public CompletableFuture<V> getAndRemove(K key) {
         return cache.removeAsync(key);
      }

      @Override
      public CompletableFuture<Void> clear() {
         return cache.clearAsync();
      }

      @Override
      public CompletableFuture<Boolean> putIfAbsent(K key, V value) {
         return cache.putIfAbsentAsync(key, value).thenApply(Objects::isNull);
      }

      @Override
      public CompletableFuture<Boolean> remove(K key, V oldValue) {
         return cache.removeAsync(key, oldValue);
      }

      @Override
      public CompletableFuture<Boolean> replace(K key, V oldValue, V newValue) {
         return cache.replaceAsync(key, oldValue, newValue);
      }

      @Override
      public CompletableFuture<Boolean> replace(K key, V value) {
         return cache.replaceAsync(key, value).thenApply(Objects::nonNull);
      }

      @Override
      public CompletableFuture<V> getAndReplace(K key, V value) {
         return cache.replaceAsync(key, value);
      }
   }
}
