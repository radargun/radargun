package org.radargun.stages.helpers;

import java.io.Serializable;

import org.radargun.config.Property;

/**
 * The policy for selecting caches in test
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CacheSelector implements Serializable {
   public static final String CACHE_SELECTOR = "CACHE_SELECTOR";

   public enum Type {
      /**
       * All threads select the default cache (retrieved by using null cache name)
       */
      DEFAULT,
      /**
       * Each thread will use cache 'cache_/threadIndex/' where /threadIndex/ is 0-based index of thread
       * (6th thread on 2nd slave with 10 threads should have thread index 15)
       */
      THREAD,
      /**
       * All threads will use specified cache name
       */
      ALL
   }

   @Property(doc = "Type of this selector. Default is DEFAULT")
   private Type type = Type.DEFAULT;

   @Property(doc = "Name of the cache returned in this selector.")
   private String cache;

   public CacheSelector(Type type, String cache) {
      this.type = type;
      this.cache = cache;
   }

   public String getCacheName(int threadIndex) {
      switch (type) {
         case DEFAULT: return null;
         case THREAD: return "cache_" + threadIndex;
         case ALL: return cache;
      }
      throw new IllegalStateException();
   }

   @Override
   public String toString() {
      return "CacheSelector(" + new Converter().convertToString(this) + ")";
   }

   public static class Converter implements org.radargun.config.Converter<CacheSelector> {
      @Override
      public CacheSelector convert(String string, java.lang.reflect.Type type) {
         int index = string.indexOf(':');
         if (index < 0) {
            return new CacheSelector(Type.valueOf(string.toUpperCase()), null);
         } else {
            return new CacheSelector(Type.valueOf(string.substring(0, index).toUpperCase()), string.substring(index + 1));
         }
      }

      @Override
      public String convertToString(CacheSelector value) {
         if (value == null) return "null";
         return value.cache == null ? value.type.name() : value.type.name() + ":" + value.cache;
      }

      @Override
      public String allowedPattern(java.lang.reflect.Type type) {
         StringBuilder sb = new StringBuilder("(");
         Type[] values = Type.values();
         for (int i = 0; i < values.length; ++i) {
            sb.append(values[i].name().toLowerCase()).append('|');
         }
         sb.append(")(:.*)?");
         return sb.toString();
      }
   }
}
