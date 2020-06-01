package org.radargun.traits;

import java.util.concurrent.CompletableFuture;

/**
 * This is an asynchronous counterpart of {@link BasicOperations}. It does not register any
 * {@link org.radargun.Operation operations} as you can reuse those from {@link BasicOperations}, the methods
 * have exactly the same semantics.
 *
 * This trait does not define if the completion of futures creates any happens-before relationship;
 * neither what happens upon {@link CompletableFuture#cancel(boolean) cancellation} of the future. Implementors
 * are likely to have different semantics and it may not be possible to guarantee one selected here.
 */
@Trait(doc = "Basic cache-like operations with asynchronous interface.")
public interface BasicAsyncOperations {

   <K, V> Cache<K, V> getCache(String cacheName);

   interface Cache<K, V> {
      /**
       * @see BasicOperations.Cache#get
       */
      CompletableFuture<V> get(K key);

      /**
       * @see BasicOperations.Cache#containsKey
       */
      CompletableFuture<Boolean> containsKey(K key);

      /**
       * @see BasicOperations.Cache#put
       */
      CompletableFuture<Void> put(K key, V value);

      /**
       * @see BasicOperations.Cache#getAndPut
       */
      CompletableFuture<V> getAndPut(K key, V value);

      /**
       * @see BasicOperations.Cache#remove
       */
      CompletableFuture<Boolean> remove(K key);

      /**
       * @see BasicOperations.Cache#getAndRemove
       */
      CompletableFuture<V> getAndRemove(K key);

      /**
       * @see BasicOperations.Cache#clear
       */
      CompletableFuture<Void> clear();
   }
}
