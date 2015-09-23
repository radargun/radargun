package org.radargun.service;

import java.util.Collections;
import java.util.List;

import org.infinispan.query.dsl.FilterConditionContext;
import org.infinispan.query.dsl.FilterConditionEndContext;
import org.infinispan.query.dsl.QueryFactory;
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

      protected FilterConditionEndContext getEndContext(Attribute attribute) {
         FilterConditionEndContext endContext;
         if (context != null) {
            endContext = context.and().having(attribute.attribute);
         } else if (builder != null) {
            endContext = builder.having(attribute.attribute);
         } else {
            endContext = factory.having(attribute.attribute);
         }
         return endContext;
      }

      @Override
      public QueryBuilder subquery() {
         return new QueryBuilderImpl(factory);
      }

      @Override
      public QueryBuilder eq(Attribute attribute, Object value) {
         context = getEndContext(attribute).eq(value);
         return this;
      }

      @Override
      public QueryBuilder lt(Attribute attribute, Object value) {
         context = getEndContext(attribute).lt(value);
         return this;
      }

      @Override
      public QueryBuilder le(Attribute attribute, Object value) {
         context = getEndContext(attribute).lte(value);
         return this;
      }

      @Override
      public QueryBuilder gt(Attribute attribute, Object value) {
         context = getEndContext(attribute).gt(value);
         return this;
      }

      @Override
      public QueryBuilder ge(Attribute attribute, Object value) {
         context = getEndContext(attribute).gte(value);
         return this;
      }

      @Override
      public QueryBuilder between(Attribute attribute, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive) {
         context = getEndContext(attribute).between(lowerBound, upperBound).includeLower(lowerInclusive).includeUpper(upperInclusive);
         return this;
      }

      @Override
      public QueryBuilder isNull(Attribute attribute) {
         context = getEndContext(attribute).isNull();
         return this;
      }

      @Override
      public QueryBuilder like(Attribute attribute, String pattern) {
         context = getEndContext(attribute).like(pattern);
         return this;
      }

      @Override
      public QueryBuilder contains(Attribute attribute, Object value) {
         context = getEndContext(attribute).contains(value);
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
      public QueryBuilder orderBy(Attribute attribute, SortOrder order) {
         if (builder == null) throw new IllegalArgumentException("You have to call orderBy() on root query builder!");
         builder.orderBy(attribute.attribute, order == SortOrder.ASCENDING ?
               org.infinispan.query.dsl.SortOrder.ASC : org.infinispan.query.dsl.SortOrder.DESC);
         return this;
      }

      @Override
      public QueryBuilder projection(Attribute... attributes) {
         if (builder == null) throw new IllegalArgumentException("You have to call projection() on root query builder!");
         String[] stringAttributes = new String[attributes.length];
         for (int i = 0; i < attributes.length; i++) {
            stringAttributes[i] = attributes[i].attribute;
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

      @Override
      public QueryResult execute() {
         return new QueryResultImpl(query.list());
      }
   }

   protected static class QueryResultImpl implements QueryResult {
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
