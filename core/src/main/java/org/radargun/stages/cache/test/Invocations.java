package org.radargun.stages.cache.test;

import java.util.Map;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.stages.test.Invocation;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Queryable;

/**
 * Provides {@link org.radargun.stages.test.Invocation} implementations for operations from traits
 * {@link org.radargun.traits.BasicOperations}, {@link org.radargun.traits.ConditionalOperations},
 * {@link org.radargun.traits.BulkOperations} and {@link org.radargun.traits.Queryable}.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Invocations {
   public static final class Get implements Invocation {
      private final static Operation GET_NULL = BasicOperations.GET.derive("Null");
      private final static Operation GET_TX = BasicOperations.GET.derive("TX");
      private final static Operation GET_NULL_TX = BasicOperations.GET.derive("TX");
      private final BasicOperations.Cache cache;
      private final Object key;
      private Object value;

      public Get(BasicOperations.Cache cache, Object key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public Object invoke() {
         return value = cache.get(key);
      }

      @Override
      public Operation operation() {
         return value == null ? GET_NULL : BasicOperations.GET;
      }

      @Override
      public Operation txOperation() {
         return value == null ? GET_NULL_TX : GET_TX;
      }
   }

   public static final class Put implements Invocation {
      private final static Operation TX = BasicOperations.PUT.derive("TX");
      private final BasicOperations.Cache cache;
      private final Object key;
      private final Object value;

      public Put(BasicOperations.Cache cache, Object key, Object value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Object invoke() {
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

   public static final class Remove implements Invocation {
      private final static Operation TX = BasicOperations.REMOVE.derive("TX");
      private final BasicOperations.Cache cache;
      private final Object key;

      public Remove(BasicOperations.Cache cache, Object key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public Object invoke() {
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

   public static final class ContainsKey implements Invocation {
      private final static Operation TX = BasicOperations.CONTAINS_KEY.derive("TX");
      private final BasicOperations.Cache cache;
      private final Object key;

      public ContainsKey(BasicOperations.Cache cache, Object key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public Object invoke() {
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

   public static final class GetAndPut implements Invocation {
      private final static Operation TX = BasicOperations.GET_AND_PUT.derive("TX");
      private final BasicOperations.Cache cache;
      private final Object key;
      private final Object value;

      public GetAndPut(BasicOperations.Cache cache, Object key, Object value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Object invoke() {
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

   public static final class GetAndRemove implements Invocation {
      private final static Operation TX = BasicOperations.GET_AND_REMOVE.derive("TX");
      private final BasicOperations.Cache cache;
      private final Object key;

      public GetAndRemove(BasicOperations.Cache cache, Object key) {
         this.cache = cache;
         this.key = key;
      }

      @Override
      public Object invoke() {
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

   public static final class PutIfAbsent implements Invocation {
      private final static Operation TX = ConditionalOperations.PUT_IF_ABSENT.derive("TX");
      private final ConditionalOperations.Cache cache;
      private final Object key;
      private final Object value;

      public PutIfAbsent(ConditionalOperations.Cache cache, Object key, Object value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Object invoke() {
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

   public static final class RemoveConditionally implements Invocation {
      private final static Operation TX = ConditionalOperations.REMOVE.derive("TX");
      private final ConditionalOperations.Cache cache;
      private final Object key;
      private final Object value;

      public RemoveConditionally(ConditionalOperations.Cache cache, Object key, Object value) {
         this.cache = cache;
         this.key = key;
         this.value = value;
      }

      @Override
      public Object invoke() {
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

   public static final class Replace implements Invocation {
      private final static Operation TX = ConditionalOperations.REPLACE.derive("TX");
      private final ConditionalOperations.Cache cache;
      private final Object key;
      private final Object oldValue;
      private final Object newValue;

      public Replace(ConditionalOperations.Cache cache, Object key, Object oldValue, Object newValue) {
         this.cache = cache;
         this.key = key;
         this.oldValue = oldValue;
         this.newValue = newValue;
      }

      @Override
      public Object invoke() {
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

   public static final class ReplaceAny implements Invocation {
      private final static Operation TX = ConditionalOperations.REPLACE_ANY.derive("TX");
      private final ConditionalOperations.Cache cache;
      private final Object key;
      private final Object newValue;

      public ReplaceAny(ConditionalOperations.Cache cache, Object key, Object newValue) {
         this.cache = cache;
         this.key = key;
         this.newValue = newValue;
      }

      @Override
      public Object invoke() {
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

   public static final class GetAndReplace implements Invocation {
      private final static Operation TX = ConditionalOperations.GET_AND_REPLACE.derive("TX");
      private final ConditionalOperations.Cache cache;
      private final Object key;
      private final Object newValue;

      public GetAndReplace(ConditionalOperations.Cache cache, Object key, Object newValue) {
         this.cache = cache;
         this.key = key;
         this.newValue = newValue;
      }

      @Override
      public Object invoke() {
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

   public static final class GetAll implements Invocation {
      private final static Operation NATIVE_TX = BulkOperations.GET_ALL_NATIVE.derive("TX");
      private final static Operation ASYNC_TX = BulkOperations.GET_ALL_ASYNC.derive("TX");
      private final BulkOperations.Cache cache;
      private final Set keys;
      private final boolean async;

      public GetAll(BulkOperations.Cache cache, boolean async, Set keys) {
         this.cache = cache;
         this.async = async;
         this.keys = keys;
      }

      @Override
      public Object invoke() {
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

   public static final class PutAll implements Invocation {
      private final static Operation NATIVE_TX = BulkOperations.PUT_ALL_NATIVE.derive("TX");
      private final static Operation ASYNC_TX = BulkOperations.PUT_ALL_ASYNC.derive("TX");
      private final BulkOperations.Cache cache;
      private final Map entries;
      private final boolean async;

      public PutAll(BulkOperations.Cache cache, boolean async, Map entries) {
         this.cache = cache;
         this.async = async;
         this.entries = entries;
      }

      @Override
      public Object invoke() {
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

   public static final class RemoveAll implements Invocation {
      private final static Operation NATIVE_TX = BulkOperations.REMOVE_ALL_NATIVE.derive("TX");
      private final static Operation ASYNC_TX = BulkOperations.REMOVE_ALL_ASYNC.derive("TX");
      private final BulkOperations.Cache cache;
      private final Set keys;
      private final boolean async;

      public RemoveAll(BulkOperations.Cache cache, boolean async, Set keys) {
         this.cache = cache;
         this.async = async;
         this.keys = keys;
      }

      @Override
      public Object invoke() {
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

   public static final class Query implements Invocation {
      protected static final Operation TX = Queryable.QUERY.derive("TX");
      private final org.radargun.traits.Query query;

      public Query(org.radargun.traits.Query query) {
         this.query = query;
      }

      @Override
      public Object invoke() {
         return query.execute();
      }

      @Override
      public Operation operation() {
         return Queryable.QUERY;
      }

      @Override
      public Operation txOperation() {
         return TX;
      }
   }
}
