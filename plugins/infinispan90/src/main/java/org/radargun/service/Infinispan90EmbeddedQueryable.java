package org.radargun.service;

import org.infinispan.query.Search;
import org.infinispan.query.dsl.Expression;
import org.infinispan.query.dsl.FilterConditionContext;
import org.infinispan.query.dsl.QueryFactory;
import org.radargun.traits.Query;

/**
 * Nothing new here, only needed because of the change in return values of methods in
 * FilterConditionEndContext.java (commit ba9fc6d0372ccdb2b2ca4b66491a1cdd87f2f1f1).
 */
public class Infinispan90EmbeddedQueryable extends Infinispan80EmbeddedQueryable {

   public Infinispan90EmbeddedQueryable(Infinispan90EmbeddedService service) {
      super(service);
   }

   @Override
   public Query.Builder getBuilder(String containerName, Class<?> clazz) {
      return new QueryBuilder90Impl(Search.getQueryFactory(service.getCache(containerName)), clazz);
   }

   protected static class QueryBuilder90Impl extends QueryBuilder80Impl {
      public QueryBuilder90Impl(QueryFactory factory, Class<?> clazz) {
         super(factory, clazz);
      }

      protected QueryBuilder90Impl(QueryFactory factory) {
         super(factory);
      }

      @Override
      public Query.Builder subquery() {
         return new QueryBuilder90Impl(factory);
      }

      @Override
      public Query.Builder eq(Query.SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).eq(value);
         return this;
      }

      @Override
      public Query.Builder lt(Query.SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).lt(value);
         return this;
      }

      @Override
      public Query.Builder le(Query.SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).lte(value);
         return this;
      }

      @Override
      public Query.Builder gt(Query.SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).gt(value);
         return this;
      }

      @Override
      public Query.Builder ge(Query.SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).gte(value);
         return this;
      }

      @Override
      public Query.Builder between(Query.SelectExpression selectExpression, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
         context = getEndContext(selectExpression).between(lowerBound, upperBound).includeLower(lowerInclusive).includeUpper(upperInclusive);
         return this;
      }

      @Override
      public Query.Builder isNull(Query.SelectExpression selectExpression) {
         context = getEndContext(selectExpression).isNull();
         return this;
      }

      @Override
      public Query.Builder like(Query.SelectExpression selectExpression, String pattern) {
         context = getEndContext(selectExpression).like(pattern);
         return this;
      }

      @Override
      public Query.Builder contains(Query.SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).contains(value);
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
      public Query.Builder projection(Query.SelectExpression... selectExpressions) {
         if (builder == null)
            throw new IllegalArgumentException("You have to call projection() on root query builder!");
         Expression[] projections = new Expression[selectExpressions.length];
         for (int i = 0; i < selectExpressions.length; i++) {
            projections[i] = attributeToExpression(selectExpressions[i]);
         }
         builder.select(projections);
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
         return new QueryImpl(builder.build());
      }
   }

}
