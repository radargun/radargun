package org.radargun.service;

import org.infinispan.query.dsl.FilterConditionContext;
import org.infinispan.query.dsl.FilterConditionEndContext;
import org.infinispan.query.dsl.QueryFactory;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;

import java.util.Collections;
import java.util.List;

/**
 * Provides implementation of querying suited to Infinispan DSL Queries
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractInfinispanQueryable implements Queryable {
   @Override
   public Query.Context createContext(String containerName) {
      return new QueryContextImpl();
   }

   protected static class QueryContextImpl implements Query.Context {
      @Override
      public void close() {
      }
   }

   protected static class QueryBuilderImpl implements Query.Builder {
      private final QueryFactory factory;
      private final org.infinispan.query.dsl.QueryBuilder builder;
      private FilterConditionContext context;

      public QueryBuilderImpl(QueryFactory factory, Class<?> clazz) {
         this.factory = factory;
         this.builder = factory.from(clazz);
      }

      protected QueryBuilderImpl(QueryFactory factory) {
         this.factory = factory;
         this.builder = null;
      }

      private FilterConditionEndContext getEndContext(String attribute) {
         FilterConditionEndContext endContext;
         if (context != null) {
            endContext = context.and().having(attribute);
         } else if (builder != null) {
            endContext = builder.having(attribute);
         } else {
            endContext = factory.having(attribute);
         }
         return endContext;
      }

      @Override
      public Query.Builder subquery() {
         return new QueryBuilderImpl(factory);
      }

      @Override
      public Query.Builder eq(String attribute, Object value) {
         context = getEndContext(attribute).eq(value);
         return this;
      }

      @Override
      public Query.Builder lt(String attribute, Object value) {
         context = getEndContext(attribute).lt(value);
         return this;
      }

      @Override
      public Query.Builder le(String attribute, Object value) {
         context = getEndContext(attribute).lte(value);
         return this;
      }

      @Override
      public Query.Builder gt(String attribute, Object value) {
         context = getEndContext(attribute).gt(value);
         return this;
      }

      @Override
      public Query.Builder ge(String attribute, Object value) {
         context = getEndContext(attribute).gte(value);
         return this;
      }

      @Override
      public Query.Builder between(String attribute, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
         context = getEndContext(attribute).between(lowerBound, upperBound).includeLower(lowerInclusive).includeUpper(upperInclusive);
         return this;
      }

      @Override
      public Query.Builder isNull(String attribute) {
         context = getEndContext(attribute).isNull();
         return this;
      }

      @Override
      public Query.Builder like(String attribute, String pattern) {
         context = getEndContext(attribute).like(pattern);
         return this;
      }

      @Override
      public Query.Builder contains(String attribute, Object value) {
         context = getEndContext(attribute).contains(value);
         return this;
      }

      @Override
      public Query.Builder not(Query.Builder subquery) {
         FilterConditionContext subqueryContext = ((QueryBuilderImpl) subquery).context;
         if (subqueryContext == null) {
            return this;
         }
         if (context != null) {
            context = context.and().not(subqueryContext);
         } else if (builder != null) {
            context = builder.not(subqueryContext);
         } else {
            context = factory.not(subqueryContext);
         }
         return this;
      }

      @Override
      public Query.Builder any(Query.Builder... subqueries) {
         if (subqueries.length == 0) {
            return this;
         }
         FilterConditionContext innerContext = null;
         for (Query.Builder subquery : subqueries) {
            if (innerContext == null) {
               innerContext = ((QueryBuilderImpl) subquery).context;
            } else {
               innerContext = innerContext.or(((QueryBuilderImpl) subquery).context);
            }
         }
         if (context != null) {
            context = context.and(innerContext);
         } else if (builder != null) {
            context = builder.not().not(innerContext);
         } else {
            context = factory.not().not(innerContext);
         }
         return this;
      }

      @Override
      public Query.Builder orderBy(String attribute, Query.SortOrder order) {
         if (builder == null) throw new IllegalArgumentException("You have to call orderBy() on root query builder!");
         builder.orderBy(attribute, order == Query.SortOrder.ASCENDING ?
               org.infinispan.query.dsl.SortOrder.ASC : org.infinispan.query.dsl.SortOrder.DESC);
         return this;
      }

      @Override
      public Query.Builder projection(String... attributes) {
         if (builder == null) throw new IllegalArgumentException("You have to call projection() on root query builder!");
         builder.setProjection(attributes);
         return this;
      }

      @Override
      public Query.Builder offset(long offset) {
         if (builder == null) throw new IllegalArgumentException("You have to call offset() on root query builder!");
         builder.startOffset(offset);
         return this;
      }

      @Override
      public Query.Builder limit(long limit) {
         if (builder == null) throw new IllegalArgumentException("You have to call limit() on root query builder!");
         builder.maxResults(limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit);
         return this;
      }

      @Override
      public Query build() {
         if (builder == null) throw new IllegalArgumentException("You have to call build() on root query builder!");
         return new QueryImpl(context.toBuilder().build());
      }
   }

   protected static class QueryImpl implements Query {
      private final org.infinispan.query.dsl.Query query;

      public QueryImpl(org.infinispan.query.dsl.Query query) {
         this.query = query;
      }

      public org.infinispan.query.dsl.Query getDelegatingQuery() {
         return query;
      }

      @Override
      public Result execute(Context resource) {
         return new QueryResultImpl(query.list());
      }
   }

   protected static class QueryResultImpl implements Query.Result {
      private final List<Object> list;

      public QueryResultImpl(List<Object> list) {
         this.list = list;
      }

      @Override
      public int size() {
         return list.size();
      }

      @Override
      public List values() {
         return Collections.unmodifiableList(list);
      }
   }
}
