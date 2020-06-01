package org.radargun.traits;

import java.util.concurrent.CompletableFuture;

/**
 * This is an asynchronous counterpart of {@link ConditionalOperations}. It does not register any
 * {@link org.radargun.Operation operations} as you can reuse those from {@link ConditionalOperations}, the methods
 * have exactly the same semantics.
 *
 * This trait does not define if the completion of futures creates any happens-before relationship;
 * neither what happens upon {@link CompletableFuture#cancel(boolean) cancellation} of the future. Implementors
 * are likely to have different semantics and it may not be possible to guarantee one selected here.
 */
@Trait(doc = "Conditional cache-like operations with asynchronous interface")
public interface ConditionalAsyncOperations {

   <K, V> Cache<K, V> getCache(String cacheName);

   interface Cache<K, V> {
      /**
       * @see ConditionalOperations.Cache#putIfAbsent
       */
      CompletableFuture<Boolean> putIfAbsent(K key, V value);

      /**
       * @see ConditionalOperations.Cache#remove
       */
      CompletableFuture<Boolean> remove(K key, V oldValue);

      /**
       * @see ConditionalOperations.Cache#replace(Object, Object, Object)
       */
      CompletableFuture<Boolean> replace(K key, V oldValue, V newValue);

      /**
       * @see ConditionalOperations.Cache#replace(Object, Object)
       */
      CompletableFuture<Boolean> replace(K key, V value);

      /**
       * @see ConditionalOperations.Cache#getAndReplace
       */
      CompletableFuture<V> getAndReplace(K key, V value);
   }
}
