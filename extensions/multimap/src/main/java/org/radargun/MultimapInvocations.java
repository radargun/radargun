package org.radargun;

import java.util.Collection;
import java.util.function.Predicate;

import org.radargun.stages.test.Invocation;
import org.radargun.traits.MultimapCacheOperations;

/**
 * The type Multimap invocations.
 */
public class MultimapInvocations {
   /**
    * The type Get.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class Get<K, V> implements Invocation<Collection<V>> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final K key;

      public Get(MultimapCacheOperations.MultimapCache<K, V> multimapCache, K key) {
         this.cache = multimapCache;
         this.key = key;
      }

      @Override
      public Collection<V> invoke() {
         try {
            return cache.get(key);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.GET.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.GET;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Put.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class Put<K, V> implements Invocation<Void> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final K key;
      private final V value;

      public Put(MultimapCacheOperations.MultimapCache<K, V> multimapCache, K key, V value) {
         this.cache = multimapCache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Void invoke() {
         try {
            return cache.put(key, value);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.PUT.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.PUT;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Remove.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class Remove<K, V> implements Invocation<Boolean> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final K key;

      public Remove(MultimapCacheOperations.MultimapCache<K, V> multimapCache, K key) {
         this.cache = multimapCache;
         this.key = key;
      }

      @Override
      public Boolean invoke() {
         try {
            return cache.remove(key);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.REMOVE.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.REMOVE;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Remove by key value.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class RemoveByKeyValue<K, V> implements Invocation<Boolean> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final K key;
      private final V value;

      public RemoveByKeyValue(MultimapCacheOperations.MultimapCache<K, V> multimapCache, K key, V value) {
         this.cache = multimapCache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Boolean invoke() {
         try {
            return cache.remove(key, value);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.REMOVE_BY_KEY_VALUE.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.REMOVE_BY_KEY_VALUE;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Remove by predicate.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class RemoveByPredicate<K, V> implements Invocation<Void> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final Predicate<? super V> predicate;

      public RemoveByPredicate(MultimapCacheOperations.MultimapCache<K, V> multimapCache, Predicate<? super V> predicate) {
         this.cache = multimapCache;
         this.predicate = predicate;
      }

      @Override
      public Void invoke() {
         try {
            return cache.remove(predicate);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.REMOVE_BY_PREDICATE.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.REMOVE_BY_PREDICATE;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Contains key.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class ContainsKey<K, V> implements Invocation<Boolean> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final K key;

      public ContainsKey(MultimapCacheOperations.MultimapCache<K, V> multimapCache, K key) {
         this.cache = multimapCache;
         this.key = key;
      }

      @Override
      public Boolean invoke() {
         try {
            return cache.containsKey(key);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.CONTAINS_KEY.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.CONTAINS_KEY;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Contains value.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class ContainsValue<K, V> implements Invocation<Boolean> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final V value;

      public ContainsValue(MultimapCacheOperations.MultimapCache<K, V> multimapCache, V value) {
         this.cache = multimapCache;
         this.value = value;
      }

      @Override
      public Boolean invoke() {
         try {
            return cache.containsValue(value);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.CONTAINS_VALUE.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.CONTAINS_VALUE;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Contains entry.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class ContainsEntry<K, V> implements Invocation<Boolean> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;
      private final K key;
      private final V value;

      public ContainsEntry(MultimapCacheOperations.MultimapCache<K, V> multimapCache, K key, V value) {
         this.cache = multimapCache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Boolean invoke() {
         try {
            return cache.containsEntry(key, value);
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.CONTAINS_ENTRY.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.CONTAINS_ENTRY;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   /**
    * The type Size.
    *
    * @param <K>  the type parameter
    * @param <V>  the type parameter
    */
   public static final class Size<K, V> implements Invocation<Long> {
      private final MultimapCacheOperations.MultimapCache<K, V> cache;

      public Size(MultimapCacheOperations.MultimapCache<K, V> multimapCache) {
         this.cache = multimapCache;
      }

      @Override
      public Long invoke() {
         try {
            return cache.size();
         } catch (Exception e) {
            throw new RuntimeException(
               "Operation " + MultimapCacheOperations.SIZE.toString() + " failed", e
            );
         }
      }

      @Override
      public Operation operation() {
         return MultimapCacheOperations.SIZE;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }
}
