package org.radargun.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.hazelcast.core.IMap;
import org.radargun.traits.BasicAsyncOperations;

import static org.radargun.service.CompletableFutureAdapter.from;
import static org.radargun.service.MappingCompletableFutureAdapter.from;

public class Hazelcast37AsyncOperations implements BasicAsyncOperations {
   private final Hazelcast36Service service;

   public Hazelcast37AsyncOperations(Hazelcast36Service service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      return new Cache(service.getMap(cacheName));
   }

   protected static class Cache<K, V> implements BasicAsyncOperations.Cache<K, V> {
      protected final IMap<K, V> map;

      public Cache(IMap<K, V> map) {
         this.map = map;
      }

      @Override
      public CompletableFuture<V> get(K key) {
         return from(map.getAsync(key));
      }

      @Override
      public CompletableFuture<Boolean> containsKey(K key) {
         return from(map.getAsync(key), Objects::nonNull);
      }

      @Override
      public CompletableFuture<Void> put(K key, V value) {
         return from(map.setAsync(key, value));
      }

      @Override
      public CompletableFuture<V> getAndPut(K key, V value) {
         return from(map.putAsync(key, value));
      }

      @Override
      public CompletableFuture<Boolean> remove(K key) {
         return from(map.removeAsync(key), Objects::nonNull);
      }

      @Override
      public CompletableFuture<V> getAndRemove(K key) {
         return from(map.removeAsync(key));
      }

      @Override
      public CompletableFuture<Void> clear() {
         throw new UnsupportedOperationException();
      }
   }
}
