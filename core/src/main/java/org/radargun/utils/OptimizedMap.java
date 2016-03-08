package org.radargun.utils;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Map optimized for holding single or none entry, but allowing more entries
 * by delegating the operations to inner map.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class OptimizedMap<K, V> implements Map<K, V>, Serializable {
   private enum Type {
      EMPTY,
      SINGLE,
      MAP
   }

   private Type type = Type.EMPTY;
   private Map<K, V> inner;
   private K singleKey;
   private V singleValue;

   @Override
   public int size() {
      return singleValue == null && inner == null ? 0 : (inner == null ? 1 : inner.size());
   }

   @Override
   public boolean isEmpty() {
      return type == Type.EMPTY || size() == 0;
   }

   @Override
   public boolean containsKey(Object key) {
      switch (type) {
         case EMPTY:
            return false;
         case SINGLE:
            return key == null ? singleKey == null : key.equals(singleKey);
         case MAP:
            return inner.containsKey(key);
      }
      throw new IllegalStateException();
   }

   @Override
   public boolean containsValue(Object value) {
      switch (type) {
         case EMPTY:
            return false;
         case SINGLE:
            return value == null ? singleValue == null : value.equals(singleKey);
         case MAP:
            return inner.containsValue(value);
      }
      throw new IllegalStateException();
   }

   @Override
   public V get(Object key) {
      switch (type) {
         case EMPTY:
            return null;
         case SINGLE:
            return key == null ? (singleKey == null ? singleValue : null)
               : (key.equals(singleKey) ? singleValue : null);
         case MAP:
            return inner.get(key);
      }
      throw new IllegalStateException();
   }

   @Override
   public V put(K key, V value) {
      switch (type) {
         case EMPTY:
            singleKey = key;
            singleValue = value;
            type = Type.SINGLE;
            return null;
         case SINGLE:
            if (key == null) {
               if (singleKey == null) {
                  return replaceValue(value);
               } else {
                  return switchToMap(key, value);
               }
            } else if (key.equals(singleKey)) {
               return replaceValue(value);
            } else {
               return switchToMap(key, value);
            }
         case MAP:
            return inner.put(key, value);
      }
      throw new IllegalStateException();
   }

   private V replaceValue(V value) {
      V temp = singleValue;
      singleValue = value;
      return temp;
   }

   private V switchToMap(K key, V value) {
      inner = new HashMap<K, V>();
      inner.put(singleKey, singleValue);
      type = Type.MAP;
      return inner.put(key, value);
   }

   @Override
   public V remove(Object key) {
      // TODO
      throw new UnsupportedOperationException();
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> m) {
      // TODO
      throw new UnsupportedOperationException();
   }

   @Override
   public void clear() {
      type = Type.EMPTY;
   }

   @Override
   public Set<K> keySet() {
      // TODO backing the set is non-trivial
      throw new UnsupportedOperationException();
   }

   @Override
   public Collection<V> values() {
      // TODO backing the set is non-trivial
      throw new UnsupportedOperationException();
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      // TODO backing the set is non-trivial
      throw new UnsupportedOperationException();
   }
}
