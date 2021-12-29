package org.radargun.service;

import org.infinispan.client.hotrod.Search;
import org.infinispan.query.dsl.QueryFactory;
import org.radargun.traits.Query;

public class Infinispan110HotrodQueryable extends Infinispan80HotrodQueryable {
   public Infinispan110HotrodQueryable(Infinispan80HotrodService service) {
      super(service);
   }

   @Override
   public Query.Builder getBuilder(String cacheName, String queryString) {
      if (cacheName == null) {
         cacheName = service.cacheName;
      }
      QueryFactory queryFactory = Search.getQueryFactory(service.managerForceReturn.getCache(cacheName));
      return new QueryBuilder110Impl(queryFactory, queryString);
   }

   protected static class QueryBuilder110Impl extends QueryBuilderImpl {

      private String queryString;

      protected QueryBuilder110Impl(QueryFactory factory, String queryString) {
         super(factory);
         this.queryString = queryString;
      }

      @Override
      public Query build() {
         return new QueryImpl(factory.create(queryString));
      }
   }
}
