package org.radargun.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
      public Builder builder() {
         return new Builder(cache);
      }

      @Override
      public boolean supportsCombiner() {
         return true;
      }

      @Override
      public boolean supportsTimeout() {
         return true;
      }

      private static class Builder implements org.radargun.traits.MapReducer.Builder {

         private ConcurrentHashMap cache;

         public Builder(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public Builder timeout(long timeout) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder source(String source) {
            return this;
         }

         @Override
         public Task build() {
            return new Task(cache);
         }

         @Override
         public Builder collator(String collatorFqn, Collection collatorParameters) {
            return this;
         }

         @Override
         public Builder combiner(String combinerFqn, Collection combinerParameters) {
            return this;
         }

         @Override
         public Builder reducer(String reducerFqn, Collection reducerParameters) {
            return this;
         }

         @Override
         public Builder mapper(String mapperFqn, Collection mapperParameters) {
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
