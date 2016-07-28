package org.radargun.utils;

import java.io.Serializable;
import java.util.Map;

/**
 * Trivial implementation of {@link java.util.Map.Entry} interface.
 */
public class MapEntry<K, V> implements Map.Entry<K, V>, Serializable {
   private final K key;
   private V value;

   public MapEntry(K key, V value) {
      this.key = key;
      this.value = value;
   }

   @Override
   public K getKey() {
      return key;
   }

   @Override
   public V getValue() {
      return value;
   }

   @Override
   public V setValue(V value) {
      V temp = this.value;
      this.value = value;
      return temp;
   }
}
