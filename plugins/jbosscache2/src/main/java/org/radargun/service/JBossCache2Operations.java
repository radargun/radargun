package org.radargun.service;

import org.jboss.cache.Fqn;
import org.radargun.traits.BasicOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JBossCache2Operations implements BasicOperations {
   protected final JBossCache2Service service;

   public JBossCache2Operations(JBossCache2Service service) {
      this.service = service;
   }

   @Override
   public <K, V> BasicOperations.Cache<K, V> getCache(String cacheName) {
      return new Cache<K, V>(service.getCache(cacheName));
   }

   protected class Cache<K, V> implements BasicOperations.Cache<K, V> {
      protected final org.jboss.cache.Cache<K, V> cache;
      protected final Fqn root;

      public Cache(org.jboss.cache.Cache<K, V> cache) {
         this.cache = cache;
         this.root = cache.getRoot().getFqn();
      }

      @Override
      public V get(K key) {
         return cache.get(root, key);
      }

      @Override
      public boolean containsKey(K key) {
         return cache.get(root, key) != null;
      }

      @Override
      public void put(K key, V value) {
         cache.put(root, key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         return cache.put(root, key, value);
      }

      @Override
      public boolean remove(K key) {
         return cache.remove(root, key) != null;
      }

      @Override
      public V getAndRemove(K key) {
         return cache.remove(root, key);
      }

      @Override
      public void clear() {
         cache.clearData(root);
      }
   }
}
