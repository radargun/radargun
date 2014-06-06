package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCache;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;

/**
 * Implementation of the {@link BasicOperations} and {@link ConditionalOperation}
 * through the HotRod protocol.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HotRodOperations implements BasicOperations, ConditionalOperations {
   protected final InfinispanHotrodService service;

   public HotRodOperations(InfinispanHotrodService service) {
      this.service = service;
   }

   @Override
   public <K, V> HotRodCache<K, V> getCache(String cacheName) {
      if (!service.manager.isStarted()) {
         service.manager.start();
      }
      if (cacheName == null) {
         return new HotRodCache<K, V>((RemoteCache<K,V>) service.manager.getCache(false), (RemoteCache<K,V>) service.manager.getCache(true));
      } else {
         return new HotRodCache<K, V>((RemoteCache<K,V>) service.manager.getCache(cacheName, false), (RemoteCache<K,V>) service.manager.getCache(cacheName, true));
      }
   }

   protected class HotRodCache<K, V> implements BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V> {

      protected final RemoteCache<K, V> noReturn;
      protected final RemoteCache<K, V> forceReturn;

      public HotRodCache(RemoteCache<K, V> noReturn, RemoteCache<K, V> forceReturn) {
         this.noReturn = noReturn;
         this.forceReturn = forceReturn;
      }

      @Override
      public V get(K key) {
         return forceReturn.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         return forceReturn.containsKey(key);
      }

      @Override
      public void put(K key, V value) {
         noReturn.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         return forceReturn.put(key, value);
      }

      @Override
      public boolean remove(K key) {
         return forceReturn.remove(key) != null;
      }

      @Override
      public V getAndRemove(K key) {
         return forceReturn.remove(key);
      }

      @Override
      public void clear() {
         noReturn.clear();
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         return forceReturn.putIfAbsent(key, value) == null;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         return forceReturn.remove(key, oldValue);
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         return forceReturn.replace(key, oldValue, newValue);
      }

      @Override
      public boolean replace(K key, V value) {
         return forceReturn.replace(key, value) != null;
      }

      @Override
      public V getAndReplace(K key, V value) {
         return forceReturn.replace(key, value);
      }
   }
}
