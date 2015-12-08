package org.radargun.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Matej Cimbora
 */
public class MapReduceTraitRepository extends CoreTraitRepository {

   public static Map<Class<?>, Object> getAllTraits() {
      Map<Class<?>, Object> traitMap = new HashMap<>(CoreTraitRepository.getAllTraits());
      ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap();
      traitMap.put(org.radargun.traits.MapReducer.class, new MapReducer(concurrentHashMap));
      return traitMap;
   }

   public static class MapReducer implements org.radargun.traits.MapReducer {

      private ConcurrentHashMap cache;

      public MapReducer(ConcurrentHashMap cache) {
         this.cache = cache;
      }

      @Override
      public Builder builder(String cacheName) {
         return new Builder(cache);
      }

      @Override
      public boolean supportsResultCacheName() {
         return true;
      }

      @Override
      public boolean supportsIntermediateSharedCache() {
         return true;
      }

      @Override
      public boolean supportsCombiner() {
         return true;
      }

      @Override
      public boolean supportsTimeout() {
         return true;
      }

      @Override
      public boolean supportsDistributedReducePhase() {
         return true;
      }

      private static class Builder implements org.radargun.traits.MapReducer.Builder {

         private ConcurrentHashMap cache;

         public Builder(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder distributedReducePhase(boolean distributedReducePhase) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder useIntermediateSharedCache(boolean useIntermediateSharedCache) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder timeout(long timeout, TimeUnit unit) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder resultCacheName(String resultCacheName) {
            return this;
         }

         @Override
         public Task build() {
            return new Task(cache);
         }

         @Override
         public org.radargun.traits.MapReducer.Builder collator(String collatorFqn, Map collatorParameters) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder combiner(String combinerFqn, Map combinerParameters) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder reducer(String reducerFqn, Map reducerParameters) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder mapper(String mapperFqn, Map mapperParameters) {
            return this;
         }
      }

      private static class Task implements org.radargun.traits.MapReducer.Task {

         private ConcurrentHashMap cache;

         public Task(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public Map execute() {
            return Collections.unmodifiableMap(cache);
         }

         @Override
         public Object executeWithCollator() {
            return cache.size();
         }
      }
   }
}
