package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.QueryFactory;
import org.radargun.traits.Query;

public class Infinispan110EmbeddedQueryable extends Infinispan90EmbeddedQueryable {

   public Infinispan110EmbeddedQueryable(Infinispan110EmbeddedService service) {
      super(service);
   }

   @Override
   public Query.Builder getBuilder(String containerName, Class<?> clazz) {
      return new QueryBuilder110Impl(Search.getQueryFactory(service.getCache(containerName)), clazz);
   }

   protected static class QueryBuilder110Impl extends QueryBuilder90Impl {

      public QueryBuilder110Impl(QueryFactory factory, Class<?> clazz) {
         super(factory, clazz);
      }

      protected QueryBuilder110Impl(QueryFactory factory) {
         super(factory);
      }

      @Override
      public Query build() {
         if (builder == null) throw new IllegalArgumentException("You have to call build() on root query builder!");
         return new Query110Impl(builder.build());
      }
   }

   protected static class Query110Impl extends QueryImpl {

      private Map<String, QueryFactory> map = new HashMap<>();

      public Query110Impl(org.infinispan.query.dsl.Query query) {
         super(query);
      }

      @Override
      public Result execute(Context resource) {
         Cache cache = ((InfinispanEmbeddedQueryable.EmbeddedQueryContext) resource).getAdvancedCache();
         QueryFactory factory = map.get(cache.getName());
         if (factory == null) {
            factory = org.infinispan.query.Search.getQueryFactory(cache);
            map.put(cache.getName(), factory);
         }
         org.infinispan.query.dsl.Query q = factory.create(query.getQueryString());
         return new QueryResultImpl(q.list());
      }
   }
}
