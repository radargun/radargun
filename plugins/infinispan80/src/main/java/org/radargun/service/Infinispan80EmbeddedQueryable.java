package org.radargun.service;

import org.infinispan.query.Search;
import org.infinispan.query.dsl.Expression;
import org.infinispan.query.dsl.FilterConditionEndContext;
import org.infinispan.query.dsl.QueryFactory;
import org.radargun.traits.Query;

/**
 * Adds support for aggregations.
 *
 * @author Jakub Markos &lt;jmarkos@redhat.com&gt;
 */
public class Infinispan80EmbeddedQueryable extends Infinispan70EmbeddedQueryable {
   public Infinispan80EmbeddedQueryable(Infinispan80EmbeddedService service) {
      super(service);
   }

   @Override
   public Query.Builder getBuilder(String cacheName, Class<?> clazz) {
      return new QueryBuilder80Impl(Search.getQueryFactory(service.getCache(cacheName)), clazz);
   }

   protected static class QueryBuilder80Impl extends QueryBuilderImpl {
      public QueryBuilder80Impl(QueryFactory factory, Class<?> clazz) {
         super(factory, clazz);
      }

      protected QueryBuilder80Impl(QueryFactory factory) {
         super(factory);
      }

      protected Expression attributeToExpression(Query.SelectExpression selectExpression) {
         switch (selectExpression.function) {
            case NONE:
               return Expression.property(selectExpression.attribute);
            case COUNT:
               return Expression.count(selectExpression.attribute);
            case AVG:
               return Expression.avg(selectExpression.attribute);
            case SUM:
               return Expression.sum(selectExpression.attribute);
            case MIN:
               return Expression.min(selectExpression.attribute);
            case MAX:
               return Expression.max(selectExpression.attribute);
            default:
               throw new RuntimeException("Unknown aggregation function: " + selectExpression.function);
         }
      }

      @Override
      protected FilterConditionEndContext getEndContext(Query.SelectExpression selectExpression) {
         FilterConditionEndContext endContext;
         if (context != null) {
            endContext = context.and().having(attributeToExpression(selectExpression));
         } else if (builder != null) {
            endContext = builder.having(attributeToExpression(selectExpression));
         } else {
            endContext = factory.having(attributeToExpression(selectExpression));
         }
         return endContext;
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
      public Query.Builder groupBy(String... attributes) {
         if (builder == null)
            throw new IllegalArgumentException("You have to call groupBy() on root query builder!");
         builder.groupBy(attributes);
         context = null; // all further 'having' conditions will start a new .having() context
         return this;
      }

      @Override
      public Query.Builder orderBy(Query.SelectExpression selectExpression) {
         if (builder == null) throw new IllegalArgumentException("You have to call orderBy() on root query builder!");
         builder.orderBy(attributeToExpression(selectExpression), selectExpression.asc ?
               org.infinispan.query.dsl.SortOrder.ASC : org.infinispan.query.dsl.SortOrder.DESC);
         return this;
      }
   }

}