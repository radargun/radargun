package org.radargun.stages.helpers;

import java.io.Serializable;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.utils.ReflexiveConverters;

/**
 * The policy for selecting caches in test
 */
public abstract class CacheSelector implements Serializable {
   public static final String CACHE_SELECTOR = "CACHE_SELECTOR";

   @DefinitionElement(name = "default", doc = "All threads select the default cache (retrieved by using null cache name)")
   public static class Default extends CacheSelector {
      @Override
      public String getCacheName(int threadIndex) {
         return null;
      }
   }

   @DefinitionElement(name = "thread", doc = "Each thread will use cache 'cache_/threadIndex/' where /threadIndex/ is 0-based index of thread " +
      "(6th thread on 2nd slave with 10 threads should have thread index 15)")
   public static class Thread extends CacheSelector {
      @Override
      public String getCacheName(int threadIndex) {
         return "cache_" + threadIndex;
      }
   }

   @DefinitionElement(name = "use-cache", doc = "All threads will use specified cache name")
   public static class UseCache extends CacheSelector {
      @Property(doc = "Name of the cache returned in this selector.")
      private String cache;

      public UseCache() {
      }

      public UseCache(String cache) {
         this.cache = cache;
      }

      @Override
      public String getCacheName(int threadIndex) {
         return cache;
      }
   }

   public abstract String getCacheName(int threadIndex);

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + PropertyHelper.toString(this);
   }

   public static class ComplexConverter extends ReflexiveConverters.ObjectConverter {
      public ComplexConverter() {
         super(new Class<?>[] {Default.class, Thread.class, UseCache.class});
      }
   }
}
