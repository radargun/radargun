package org.radargun.service;

import java.util.Collections;
import java.util.List;

import org.infinispan.query.dsl.FilterConditionContext;
import org.infinispan.query.dsl.FilterConditionEndContext;
import org.infinispan.query.dsl.QueryFactory;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;

/**
 * Provides implementation of querying suited to Infinispan DSL Queries
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractInfinispanQueryable implements Queryable {

   protected static class QueryBuilderImpl implements QueryBuilder {
      protected final QueryFactory factory;
      protected final org.infinispan.query.dsl.QueryBuilder builder;
      protected FilterConditionContext context;

      public QueryBuilderImpl(QueryFactory factory, Class<?> clazz) {
         this.factory = factory;
         this.builder = factory.from(clazz);
      }

      protected QueryBuilderImpl(QueryFactory factory) {
         this.factory = factory;
         this.builder = null;
      }

      protected FilterConditionEndContext getEndContext(SelectExpression selectExpression) {
         FilterConditionEndContext endContext;
         if (context != null) {
            endContext = context.and().having(selectExpression.attribute);
         } else if (builder != null) {
            endContext = builder.having(selectExpression.attribute);
         } else {
            endContext = factory.having(selectExpression.attribute);
         }
         return endContext;
      }

      @Override
      public QueryBuilder subquery() {
         return new QueryBuilderImpl(factory);
      }

      @Override
      public QueryBuilder eq(SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).eq(value);
         return this;
      }

      @Override
      public QueryBuilder lt(SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).lt(value);
         return this;
      }

      @Override
      public QueryBuilder le(SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).lte(value);
         return this;
      }

      @Override
      public QueryBuilder gt(SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).gt(value);
         return this;
      }

      @Override
      public QueryBuilder ge(SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).gte(value);
         return this;
      }

      @Override
      public QueryBuilder between(SelectExpression selectExpression, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
         context = getEndContext(selectExpression).between(lowerBound, upperBound).includeLower(lowerInclusive).includeUpper(upperInclusive);
         return this;
      }

      @Override
      public QueryBuilder isNull(SelectExpression selectExpression) {
         context = getEndContext(selectExpression).isNull();
         return this;
      }

      @Override
      public QueryBuilder like(SelectExpression selectExpression, String pattern) {
         context = getEndContext(selectExpression).like(pattern);
         return this;
      }

      @Override
      public QueryBuilder contains(SelectExpression selectExpression, Object value) {
         context = getEndContext(selectExpression).contains(value);
         return this;
      }

      @Override
      public QueryBuilder not(QueryBuilder subquery) {
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
      public QueryBuilder any(QueryBuilder... subqueries) {
         if (subqueries.length == 0) {
            return this;
         }
         FilterConditionContext innerContext = null;
         for (QueryBuilder subquery : subqueries) {
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
      public QueryBuilder orderBy(SelectExpression selectExpression, SortOrder order) {
         if (builder == null) throw new IllegalArgumentException("You have to call orderBy() on root query builder!");
         builder.orderBy(selectExpression.attribute, order == SortOrder.ASCENDING ?
               org.infinispan.query.dsl.SortOrder.ASC : org.infinispan.query.dsl.SortOrder.DESC);
         return this;
      }

      @Override
      public QueryBuilder projection(SelectExpression... selectExpressions) {
         if (builder == null) throw new IllegalArgumentException("You have to call projection() on root query builder!");
         String[] stringAttributes = new String[selectExpressions.length];
         for (int i = 0; i < selectExpressions.length; i++) {
            stringAttributes[i] = selectExpressions[i].attribute;
         }
         builder.setProjection(stringAttributes);
         return this;
      }

      @Override
      public QueryBuilder groupBy(String... attributes) {
         throw new RuntimeException("Grouping is not implemented in this version of infinispan.");
      }

      @Override
      public QueryBuilder offset(long offset) {
         if (builder == null) throw new IllegalArgumentException("You have to call offset() on root query builder!");
         builder.startOffset(offset);
         return this;
      }

      @Override
      public QueryBuilder limit(long limit) {
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

   protected static class QueryImpl implements Query {
      private final org.infinispan.query.dsl.Query query;

      public QueryImpl(org.infinispan.query.dsl.Query query) {
         this.query = query;
      }

      public org.infinispan.query.dsl.Query getDelegatingQuery() {
         return query;
      }

      @Override
      public QueryResult execute() {
         return new QueryResultImpl(query.list());
      }
   }

   protected static class QueryResultImpl implements Query.QueryResult {
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
