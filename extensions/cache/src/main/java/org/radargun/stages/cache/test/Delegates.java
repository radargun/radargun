package org.radargun.stages.cache.test;

import java.util.Map;
import java.util.Set;

import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.TemporalOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Delegates {

   public static class BasicOperationsCache<K, V> implements BasicOperations.Cache<K, V> {
      private BasicOperations.Cache<K, V> delegate;

      public BasicOperations.Cache<K, V> getDelegate() {
         return delegate;
      }

      public void setDelegate(BasicOperations.Cache<K, V> delegate) {
         this.delegate = delegate;
      }

      @Override
      public V get(K key) {
         return delegate.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         return delegate.containsKey(key);
      }

      @Override
      public void put(K key, V value) {
         delegate.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         return delegate.getAndPut(key, value);
      }

      @Override
      public boolean remove(K key) {
         return delegate.remove(key);
      }

      @Override
      public V getAndRemove(K key) {
         return delegate.getAndRemove(key);
      }

      @Override
      public void clear() {
         delegate.clear();
      }
   }

   public static class BulkOperationsCache<K, V> implements BulkOperations.Cache<K, V> {
      private BulkOperations.Cache<K, V> delegate;

      public BulkOperations.Cache<K, V> getDelegate() {
         return delegate;
      }

      public void setDelegate(BulkOperations.Cache<K, V> delegate) {
         this.delegate = delegate;
      }

      @Override
      public Map<K, V> getAll(Set<K> keys) {
         return delegate.getAll(keys);
      }

      @Override
      public void putAll(Map<K, V> entries) {
         delegate.putAll(entries);
      }

      @Override
      public void removeAll(Set<K> keys) {
         delegate.removeAll(keys);
      }
   }

   public static class ConditionalOperationsCache<K, V> implements ConditionalOperations.Cache<K, V> {
      private ConditionalOperations.Cache<K, V> delegate;

      public ConditionalOperations.Cache<K, V> getDelegate() {
         return delegate;
      }

      public void setDelegate(ConditionalOperations.Cache<K, V> delegate) {
         this.delegate = delegate;
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         return delegate.putIfAbsent(key, value);
      }

      @Override
      public boolean remove(K key, V oldValue) {
         return delegate.remove(key, oldValue);
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         return delegate.replace(key, oldValue, newValue);
      }

      @Override
      public boolean replace(K key, V value) {
         return delegate.replace(key, value);
      }

      @Override
      public V getAndReplace(K key, V value) {
         return delegate.getAndReplace(key, value);
      }
   }

   public static class TemporalOperationsCache<K, V> implements TemporalOperations.Cache<K, V> {
      private TemporalOperations.Cache<K, V> delegate;

      public TemporalOperations.Cache<K, V> getDelegate() {
         return delegate;
      }

      public void setDelegate(TemporalOperations.Cache<K, V> delegate) {
         this.delegate = delegate;
      }

      @Override
      public void put(K key, V value, long lifespan) {
         delegate.put(key, value, lifespan);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan) {
         return delegate.getAndPut(key, value, lifespan);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan) {
         return delegate.putIfAbsent(key, value, lifespan);
      }

      @Override
      public void put(K key, V value, long lifespan, long maxIdleTime) {
         delegate.put(key, value, lifespan, maxIdleTime);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan, long maxIdleTime) {
         return delegate.getAndPut(key, value, lifespan, maxIdleTime);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan, long maxIdleTime) {
         return delegate.putIfAbsent(key, value, lifespan, maxIdleTime);
      }
   }
}
