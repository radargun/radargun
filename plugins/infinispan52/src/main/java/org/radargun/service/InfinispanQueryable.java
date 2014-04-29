package org.radargun.service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.dsl.TermTermination;
import org.infinispan.Cache;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.radargun.traits.Queryable;

/**
 * Wrapper which will be able to run queries on Infinispan caches.
 *
 * @author Anna Manukyan
 */
public class InfinispanQueryable implements Queryable {

   protected Infinispan52EmbeddedService wrapper;

   public InfinispanQueryable(Infinispan52EmbeddedService wrapper) {
      this.wrapper = wrapper;
   }

   @Override
   public QueryResultImpl executeQuery(Map<String, Object> queryParameters) {
      Cache cache = wrapper.getCache(null);

      SearchManager searchManager = Search.getSearchManager(cache);
      QueryBuilder queryBuilder = searchManager.buildQueryBuilderForClass(QueryableData.class).get();
      TermTermination termTermination;
      Boolean isWildcardQuery = (Boolean) queryParameters.get(IS_WILDCARD);
      String onField = (String) queryParameters.get(QUERYABLE_FIELD);
      String matching = (String) queryParameters.get(MATCH_STRING);

      if (isWildcardQuery) {
         termTermination = queryBuilder.keyword().wildcard().onField(onField).matching(matching);
      } else {
         termTermination = queryBuilder.keyword().onField(onField).matching(matching);
      }

      CacheQuery cacheQuery = searchManager.getQuery(termTermination.createQuery());

      return new QueryResultImpl(cacheQuery);
   }

   static Object wrapForQuery(Object value) {
      return new QueryableData((String) value);
   }

   @Indexed(index = "query")
   public static class QueryableData implements Serializable {

      @Field(store = Store.YES)
      private String description;

      public QueryableData(String description) {
         this.description = description;
      }

      public String getDescription() {
         return description;
      }

      public String toString() {
         return description;
      }
   }

   public class QueryResultImpl implements QueryResult {
      private CacheQuery cacheQuery;

      public QueryResultImpl(final CacheQuery cacheQuery) {
         this.cacheQuery = cacheQuery;
      }

      public int size() {
         return cacheQuery.getResultSize();
      }

      public List list() {
         return cacheQuery.list();
      }
   }
}
