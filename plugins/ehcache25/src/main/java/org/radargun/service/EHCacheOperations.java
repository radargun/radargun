package org.radargun.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class EHCacheOperations implements BasicOperations, ConditionalOperations, BulkOperations {

   protected final EHCacheService service;

   public EHCacheOperations(EHCacheService service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      return new Cache<K, V>(service.getCache(cacheName));
   }

   @Override
   public <K, V> BulkOperations.Cache<K, V> getCache(String cacheName, boolean preferAsync) {
      return getCache(cacheName);
   }

   protected class Cache<K, V> implements BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V>, BulkOperations.Cache<K, V> {
      protected final Ehcache cache;

      public Cache(Ehcache cache) {
         this.cache = cache;
      }

      @Override
      public V get(K key) {
         Element element = cache.get(key);
         return element == null ? null : (V) element.getObjectValue();
      }

      @Override
      public boolean containsKey(K key) {
         return cache.get(key) != null;
      }

      @Override
      public void put(K key, V value) {
         cache.put(new Element(key, value));
      }

      @Override
      public V getAndPut(K key, V value) {
         Element element = new Element(key, value);
         for (; ; ) {
            Element prev = cache.replace(element);
            if (prev == null) {
               prev = cache.putIfAbsent(element);
               if (prev == null) return null;
            } else {
               return (V) prev.getObjectValue();
            }
         }
      }

      @Override
      public boolean remove(K key) {
         return cache.remove(key);
      }

      @Override
      public V getAndRemove(K key) {
         for (; ; ) {
            Element prev = cache.get(key);
            if (prev == null) return null;
            if (cache.removeElement(prev)) {
               return (V) prev.getObjectValue();
            }
         }
      }

      @Override
      public boolean replace(K key, V value) {
         Element element = new Element(key, value);
         Element prev = cache.replace(element);
         return prev != null;
      }

      @Override
      public V getAndReplace(K key, V value) {
         Element element = new Element(key, value);
         Element prev = cache.replace(element);
         return prev != null ? (V) prev.getObjectValue() : null;
      }

      @Override
      public void clear() {
         cache.removeAll();
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         Element element = new Element((Serializable) key, (Serializable) value);
         Element previous = cache.putIfAbsent(element);
         return previous == null;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         return cache.removeElement(new Element(key, oldValue));
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         return cache.replace(new Element(key, oldValue), new Element(key, newValue));
      }

      @Override
      public Map<K, V> getAll(Set<K> keys) {
         Map<K, V> map = new HashMap<K, V>();
         Map<Object, Element> elements = cache.getAll(keys);
         for (Element element : elements.values()) {
            if (element == null) continue;
            map.put((K) element.getObjectKey(), (V) element.getObjectValue());
         }
         return map;
      }

      @Override
      public void putAll(Map<K, V> entries) {
         ArrayList<Element> elements = new ArrayList<Element>(entries.size());
         for (Map.Entry<K, V> entry : entries.entrySet()) {
            elements.add(new Element(entry.getKey(), entry.getValue()));
         }
         cache.putAll(elements);
      }

      @Override
      public void removeAll(Set<K> keys) {
         cache.removeAll(keys);
      }
   }
}
