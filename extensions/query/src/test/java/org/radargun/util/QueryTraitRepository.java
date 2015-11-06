package org.radargun.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Matej Cimbora
 */
public class QueryTraitRepository extends CoreTraitRepository {

   public static Map<Class<?>, Object> getAllTraits() {
      Map<Class<?>, Object> traitMap = new HashMap<>(CoreTraitRepository.getAllTraits());
      ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap();
      traitMap.put(org.radargun.traits.Queryable.class, new Queryable(concurrentHashMap));
      traitMap.put(org.radargun.traits.ContinuousQuery.class, new ContinuousQuery());
      return traitMap;
   }

   public static class Queryable implements org.radargun.traits.Queryable {

      private ConcurrentHashMap cache;

      public Queryable(ConcurrentHashMap cache) {
         this.cache = cache;
      }

      @Override
      public Query.Builder getBuilder(String containerName, Class<?> clazz) {
         return new Builder(cache);
      }

      @Override
      public Query.Context createContext(String containerName) {
         return new Context();
      }

      @Override
      public void reindex(String containerName) {
         // no op
      }

      public Map getCache() {
         return Collections.unmodifiableMap(cache);
      }

      public void setCache(ConcurrentHashMap cache) {
         this.cache = cache;
      }

      private static class Builder implements org.radargun.traits.Query.Builder {

         private ConcurrentHashMap cache;

         public Builder(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public Query.Builder subquery() {
            return this;
         }

         @Override
         public Query.Builder eq(String attribute, Object value) {
            return this;
         }

         @Override
         public Query.Builder lt(String attribute, Object value) {
            return this;
         }

         @Override
         public Query.Builder le(String attribute, Object value) {
            return this;
         }

         @Override
         public Query.Builder gt(String attribute, Object value) {
            return this;
         }

         @Override
         public Query.Builder ge(String attribute, Object value) {
            return this;
         }

         @Override
         public Query.Builder between(String attribute, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
            return this;
         }

         @Override
         public Query.Builder isNull(String attribute) {
            return this;
         }

         @Override
         public Query.Builder like(String attribute, String pattern) {
            return this;
         }

         @Override
         public Query.Builder contains(String attribute, Object value) {
            return this;
         }

         @Override
         public Query.Builder not(Query.Builder subquery) {
            return this;
         }

         @Override
         public Query.Builder any(Query.Builder... subqueries) {
            return this;
         }

         @Override
         public Query.Builder orderBy(String attribute, Query.SortOrder order) {
            return this;
         }

         @Override
         public Query.Builder projection(String... attribute) {
            return this;
         }

         @Override
         public Query.Builder offset(long offset) {
            return this;
         }

         @Override
         public Query.Builder limit(long limit) {
            return this;
         }

         @Override
         public Query build() {
            return new Query(cache);
         }
      }

      private static class Query implements org.radargun.traits.Query {

         private ConcurrentHashMap cache;

         public Query(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public Result execute(Context context) {
            return new Queryable.Result(cache);
         }
      }

      private static class Result implements org.radargun.traits.Query.Result {

         private ConcurrentHashMap cache;

         public Result(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public int size() {
            return cache.size();
         }

         @Override
         public Collection values() {
            return Collections.unmodifiableCollection(cache.values());
         }
      }

      private static class Context implements org.radargun.traits.Query.Context {

         @Override
         public void close() {
            // no op
         }
      }
   }

   public static class ContinuousQuery implements org.radargun.traits.ContinuousQuery {

      private Map<String, ContinuousQueryListener> cacheCqMap = new HashMap<>();

      @Override
      public void createContinuousQuery(String cacheName, org.radargun.traits.Query query, org.radargun.traits.ContinuousQuery.ContinuousQueryListener cqListener) {
         cacheCqMap.put(cacheName, cqListener);
      }

      @Override
      public void removeContinuousQuery(String cacheName, Object cqListener) {
         cacheCqMap.remove(cacheName, cqListener);
      }

      public Map<String, ContinuousQueryListener> getCacheCqMap() {
         return Collections.unmodifiableMap(cacheCqMap);
      }
   }
}
