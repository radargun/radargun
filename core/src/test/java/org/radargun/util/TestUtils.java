package org.radargun.util;

import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.traits.BasicOperations;

import java.util.concurrent.ConcurrentHashMap;

/**
 * General test tools.
 *
 * @author Matej Cimbora
 */
public final class TestUtils {

   private TestUtils() {
   }

   public static class TestException extends RuntimeException {

      public TestException() {
      }
   }

   public static class SimpleStringKeyGenerator implements KeyGenerator {

      @Override
      public Object generateKey(long keyIndex) {
         return String.valueOf(keyIndex);
      }
   }

   public static class BasicOperationsCache<K, V> implements BasicOperations.Cache<K, V> {

      private ConcurrentHashMap<K, V> container = new ConcurrentHashMap<>();

      @Override
      public Object get(Object key) {
         return container.get(key);
      }

      @Override
      public boolean containsKey(Object key) {
         return container.containsKey(key);
      }

      @Override
      public void put(Object key, Object value) {
         container.put((K) key, (V) value);
      }

      @Override
      public Object getAndPut(Object key, Object value) {
         return container.put((K) key, (V) value);
      }

      @Override
      public boolean remove(Object key) {
         return container.remove(key) != null;
      }

      @Override
      public Object getAndRemove(Object key) {
         return container.remove(key);
      }

      @Override
      public void clear() {
         container.clear();
      }
   }
}
