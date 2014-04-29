package org.radargun.service;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan52Operations extends Infinispan51Operations {
   protected final Infinispan52EmbeddedService service;

   public Infinispan52Operations(Infinispan52EmbeddedService service) {
      super(service);
      this.service = service;
   }

   @Override
   public <K, V> InfinispanCache<K, V> getCache(String cacheName) {
      InfinispanCache<K, V> cache = super.getCache(cacheName);
      if (service.wrapForQuery) {
         return new WrappingCache<K, V>(cache);
      }
      return cache;
   }

   protected class WrappingCache<K, V> implements InfinispanCache<K, V> {
      protected final InfinispanCache wrapped;

      public WrappingCache(InfinispanCache<K, V> cache) {
         this.wrapped = cache;
      }

      @Override
      public V get(K key) {
         return (V) wrapped.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         return wrapped.containsKey(key);
      }

      @Override
      public void put(K key, V value) {
         wrapped.put(key, service.wrapValue(value));
      }

      @Override
      public V getAndPut(K key, V value) {
         return (V) wrapped.getAndPut(key, service.wrapValue(value));
      }

      @Override
      public boolean remove(K key) {
         return wrapped.remove(key);
      }

      @Override
      public V getAndRemove(K key) {
         return (V) wrapped.getAndRemove(key);
      }

      @Override
      public boolean replace(K key, V value) {
         return wrapped.replace(key, service.wrapValue(value));
      }

      @Override
      public V getAndReplace(K key, V value) {
         return (V) wrapped.getAndReplace(key, service.wrapValue(value));
      }

      @Override
      public void clear() {
         wrapped.clear();
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         return wrapped.putIfAbsent(key, service.wrapValue(value));
      }

      @Override
      public boolean remove(K key, V oldValue) {
         return wrapped.remove(key, service.wrapValue(oldValue));
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         return wrapped.replace(key, service.wrapValue(oldValue), service.wrapValue(newValue));
      }
   }
}
