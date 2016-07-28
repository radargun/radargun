package org.radargun.stages.cache.test;

import java.util.Map;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.stages.test.Invocation;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.TemporalOperations;

/**
 * Provides {@link org.radargun.stages.test.Invocation} implementations for operations from traits
 * {@link org.radargun.traits.BasicOperations}, {@link org.radargun.traits.ConditionalOperations},
 * and {@link org.radargun.traits.BulkOperations}.
 */
public class CacheInvocations {
   public static final class Get<K, V> implements Invocation<V> {
      public static final Operation GET_NULL = BasicOperations.GET.derive("Null");
      public static final Operation TX = BasicOperations.GET.derive("TX");
      private final BasicOperations.Cache<K, V> cache;
      private final K key;
      private V value;

      public Get(BasicOperations.Cache cache, K key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public V invoke() {
         return value = cache.get(key);
      }

      @Override
      public Operation operation() {
         return value == null ? GET_NULL : BasicOperations.GET;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class Put<K, V> implements Invocation<Void> {
      public static final Operation TX = BasicOperations.PUT.derive("TX");
      private final BasicOperations.Cache<K, V> cache;
      private final K key;
      private final V value;

      public Put(BasicOperations.Cache<K, V> cache, K key, V value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Void invoke() {
         cache.put(key, value);
         return null;
      }

      @Override
      public Operation operation() {
         return BasicOperations.PUT;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class PutWithLifespan<K, V> implements Invocation<V> {
      private static final Operation TX = TemporalOperations.PUT_WITH_LIFESPAN.derive("TX");
      private final TemporalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;
      private final long lifespan;

      public PutWithLifespan(TemporalOperations.Cache<K, V> cache, K key, V value, long lifespan) {
         this.cache = cache;
         this.key = key;
         this.value = value;
         this.lifespan = lifespan;
      }

      @Override
      public V invoke() {
         cache.put(key, value, lifespan);
         return null;
      }

      @Override
      public Operation operation() {
         return TemporalOperations.PUT_WITH_LIFESPAN;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class PutWithLifespanAndMaxIdle<K, V> implements Invocation<Void> {
      private static final Operation TX = TemporalOperations.PUT_WITH_LIFESPAN_AND_MAXIDLE.derive("TX");
      private final TemporalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;
      private final long lifespan;
      private final long maxIdle;

      public PutWithLifespanAndMaxIdle(TemporalOperations.Cache<K, V> cache, K key, V value, long lifespan, long maxIdle) {
         this.cache = cache;
         this.key = key;
         this.value = value;
         this.lifespan = lifespan;
         this.maxIdle = maxIdle;
      }

      @Override
      public Void invoke() {
         cache.put(key, value, lifespan, maxIdle);
         return null;
      }

      @Override
      public Operation operation() {
         return TemporalOperations.PUT_WITH_LIFESPAN_AND_MAXIDLE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class Remove<K, V> implements Invocation<Boolean> {
      public static final Operation TX = BasicOperations.REMOVE.derive("TX");
      private final BasicOperations.Cache<K, V> cache;
      private final K key;

      public Remove(BasicOperations.Cache cache, K key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public Boolean invoke() {
         return cache.remove(key);
      }

      @Override
      public Operation operation() {
         return BasicOperations.REMOVE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class ContainsKey<K> implements Invocation<Boolean> {
      public static final Operation TX = BasicOperations.CONTAINS_KEY.derive("TX");
      private final BasicOperations.Cache<K, ?> cache;
      private final K key;

      public ContainsKey(BasicOperations.Cache<K, ?> cache, K key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public Boolean invoke() {
         return cache.containsKey(key);
      }

      @Override
      public Operation operation() {
         return BasicOperations.CONTAINS_KEY;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class GetAndPut<K, V> implements Invocation<V> {
      public static final Operation TX = BasicOperations.GET_AND_PUT.derive("TX");
      private final BasicOperations.Cache<K, V> cache;
      private final K key;
      private final V value;

      public GetAndPut(BasicOperations.Cache<K, V> cache, K key, V value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public V invoke() {
         return cache.getAndPut(key, value);
      }

      @Override
      public Operation operation() {
         return BasicOperations.GET_AND_PUT;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class GetAndPutWithLifespan<K, V> implements Invocation<V> {
      private static final Operation TX = TemporalOperations.GET_AND_PUT_WITH_LIFESPAN.derive("TX");
      private final TemporalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;
      private final long lifespan;

      public GetAndPutWithLifespan(TemporalOperations.Cache<K, V> cache, K key, V value, long lifespan) {
         this.cache = cache;
         this.key = key;
         this.value = value;
         this.lifespan = lifespan;
      }

      @Override
      public V invoke() {
         return cache.getAndPut(key, value, lifespan);
      }

      @Override
      public Operation operation() {
         return TemporalOperations.GET_AND_PUT_WITH_LIFESPAN;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class GetAndPutWithLifespanAndMaxIdle<K, V> implements Invocation<V> {
      private static final Operation TX = TemporalOperations.GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE.derive("TX");
      private final TemporalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;
      private final long lifespan;
      private final long maxIdle;

      public GetAndPutWithLifespanAndMaxIdle(TemporalOperations.Cache<K, V> cache, K key, V value, long lifespan, long maxIdle) {
         this.cache = cache;
         this.key = key;
         this.value = value;
         this.lifespan = lifespan;
         this.maxIdle = maxIdle;
      }

      @Override
      public V invoke() {
         return cache.getAndPut(key, value, lifespan, maxIdle);
      }

      @Override
      public Operation operation() {
         return TemporalOperations.GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class GetAndRemove<K, V> implements Invocation<V> {
      public static final Operation TX = BasicOperations.GET_AND_REMOVE.derive("TX");
      private final BasicOperations.Cache<K, V> cache;
      private final K key;

      public GetAndRemove(BasicOperations.Cache<K, V> cache, K key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public V invoke() {
         return cache.getAndRemove(key);
      }

      @Override
      public Operation operation() {
         return BasicOperations.GET_AND_REMOVE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class PutIfAbsent<K, V> implements Invocation<Boolean> {
      private static final Operation TX = ConditionalOperations.PUT_IF_ABSENT.derive("TX");
      private final ConditionalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;

      public PutIfAbsent(ConditionalOperations.Cache<K, V> cache, K key, V value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Boolean invoke() {
         return cache.putIfAbsent(key, value);
      }

      @Override
      public Operation operation() {
         return ConditionalOperations.PUT_IF_ABSENT;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class PutIfAbsentWithLifespan<K, V> implements Invocation<Boolean> {
      private static final Operation TX = TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN.derive("TX");
      private final TemporalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;
      private final long lifespan;

      public PutIfAbsentWithLifespan(TemporalOperations.Cache<K, V> cache, K key, V value, long lifespan) {
         this.cache = cache;
         this.key = key;
         this.value = value;
         this.lifespan = lifespan;
      }

      @Override
      public Boolean invoke() {
         return cache.putIfAbsent(key, value, lifespan);
      }

      @Override
      public Operation operation() {
         return TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class PutIfAbsentWithLifespanAndMaxIdle<K, V> implements Invocation<Boolean> {
      private static final Operation TX = TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE.derive("TX");
      private final TemporalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;
      private final long lifespan;
      private final long maxIdle;

      public PutIfAbsentWithLifespanAndMaxIdle(TemporalOperations.Cache<K, V> cache, K key, V value, long lifespan, long maxIdle) {
         this.cache = cache;
         this.key = key;
         this.value = value;
         this.lifespan = lifespan;
         this.maxIdle = maxIdle;
      }

      @Override
      public Boolean invoke() {
         return cache.putIfAbsent(key, value, lifespan, maxIdle);
      }

      @Override
      public Operation operation() {
         return TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class RemoveConditionally<K, V> implements Invocation<Boolean> {
      private static final Operation TX = ConditionalOperations.REMOVE.derive("TX");
      private final ConditionalOperations.Cache<K, V> cache;
      private final K key;
      private final V value;

      public RemoveConditionally(ConditionalOperations.Cache<K, V> cache, K key, V value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Boolean invoke() {
         return cache.remove(key, value);
      }

      @Override
      public Operation operation() {
         return ConditionalOperations.REMOVE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class Replace<K, V> implements Invocation<Boolean> {
      private static final Operation TX = ConditionalOperations.REPLACE.derive("TX");
      private final ConditionalOperations.Cache<K, V> cache;
      private final K key;
      private final V oldValue;
      private final V newValue;

      public Replace(ConditionalOperations.Cache<K, V> cache, K key, V oldValue, V newValue) {
         this.cache = cache;
         this.key = key;
         this.oldValue = oldValue;
         this.newValue = newValue;
      }

      @Override
      public Boolean invoke() {
         return cache.replace(key, oldValue, newValue);
      }

      @Override
      public Operation operation() {
         return ConditionalOperations.REPLACE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class ReplaceAny<K, V> implements Invocation<Boolean> {
      private static final Operation TX = ConditionalOperations.REPLACE_ANY.derive("TX");
      private final ConditionalOperations.Cache<K, V> cache;
      private final K key;
      private final V newValue;

      public ReplaceAny(ConditionalOperations.Cache<K, V> cache, K key, V newValue) {
         this.cache = cache;
         this.key = key;
         this.newValue = newValue;
      }

      @Override
      public Boolean invoke() {
         return cache.replace(key, newValue);
      }

      @Override
      public Operation operation() {
         return ConditionalOperations.REPLACE_ANY;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class GetAndReplace<K, V> implements Invocation<V> {
      private static final Operation TX = ConditionalOperations.GET_AND_REPLACE.derive("TX");
      private final ConditionalOperations.Cache<K, V> cache;
      private final K key;
      private final V newValue;

      public GetAndReplace(ConditionalOperations.Cache<K, V> cache, K key, V newValue) {
         this.cache = cache;
         this.key = key;
         this.newValue = newValue;
      }

      @Override
      public V invoke() {
         return cache.getAndReplace(key, newValue);
      }

      @Override
      public Operation operation() {
         return ConditionalOperations.GET_AND_REPLACE;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }

   public static final class GetAll<K, V> implements Invocation<Map<K, V>> {
      private static final Operation NATIVE_TX = BulkOperations.GET_ALL_NATIVE.derive("TX");
      private static final Operation ASYNC_TX = BulkOperations.GET_ALL_ASYNC.derive("TX");
      private final BulkOperations.Cache<K, V> cache;
      private final Set<K> keys;
      private final boolean async;

      public GetAll(BulkOperations.Cache<K, V> cache, boolean async, Set<K> keys) {
         this.cache = cache;
         this.async = async;
         this.keys = keys;
      }

      @Override
      public Map<K, V> invoke() {
         return cache.getAll(keys);
      }

      @Override
      public Operation operation() {
         return async ? BulkOperations.GET_ALL_ASYNC : BulkOperations.GET_ALL_NATIVE;
      }

      @Override
      public Operation txOperation() {
         return async ? ASYNC_TX : NATIVE_TX;
      }
   }

   public static final class PutAll<K, V> implements Invocation<Void> {
      private static final Operation NATIVE_TX = BulkOperations.PUT_ALL_NATIVE.derive("TX");
      private static final Operation ASYNC_TX = BulkOperations.PUT_ALL_ASYNC.derive("TX");
      private final BulkOperations.Cache<K, V> cache;
      private final Map<K, V> entries;
      private final boolean async;

      public PutAll(BulkOperations.Cache<K, V> cache, boolean async, Map<K, V> entries) {
         this.cache = cache;
         this.async = async;
         this.entries = entries;
      }

      @Override
      public Void invoke() {
         cache.putAll(entries);
         return null;
      }

      @Override
      public Operation operation() {
         return async ? BulkOperations.PUT_ALL_ASYNC : BulkOperations.PUT_ALL_NATIVE;
      }

      @Override
      public Operation txOperation() {
         return async ? ASYNC_TX : NATIVE_TX;
      }
   }

   public static final class RemoveAll<K, V> implements Invocation<Void> {
      private static final Operation NATIVE_TX = BulkOperations.REMOVE_ALL_NATIVE.derive("TX");
      private static final Operation ASYNC_TX = BulkOperations.REMOVE_ALL_ASYNC.derive("TX");
      private final BulkOperations.Cache<K, V> cache;
      private final Set<K> keys;
      private final boolean async;

      public RemoveAll(BulkOperations.Cache<K, V> cache, boolean async, Set<K> keys) {
         this.cache = cache;
         this.async = async;
         this.keys = keys;
      }

      @Override
      public Void invoke() {
         cache.removeAll(keys);
         return null;
      }

      @Override
      public Operation operation() {
         return async ? BulkOperations.REMOVE_ALL_ASYNC : BulkOperations.REMOVE_ALL_NATIVE;
      }

      @Override
      public Operation txOperation() {
         return async ? ASYNC_TX : NATIVE_TX;
      }
   }
}
