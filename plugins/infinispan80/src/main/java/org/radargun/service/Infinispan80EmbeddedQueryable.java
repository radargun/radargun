package org.radargun.service;

import org.infinispan.query.Search;
import org.infinispan.query.dsl.Expression;
import org.infinispan.query.dsl.FilterConditionEndContext;
import org.infinispan.query.dsl.QueryFactory;

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
   public QueryBuilder getBuilder(String cacheName, Class<?> clazz) {
      return new QueryBuilder80Impl(Search.getQueryFactory(service.getCache(cacheName)), clazz);
   }

   protected static class QueryBuilder80Impl extends QueryBuilderImpl {
      public QueryBuilder80Impl(QueryFactory factory, Class<?> clazz) {
         super(factory, clazz);
      }

      protected QueryBuilder80Impl(QueryFactory factory) {
         super(factory);
      }

      protected Expression attributeToExpression(Attribute attribute) {
         switch (attribute.function) {
            case NONE:
               return Expression.property(attribute.attribute);
            case COUNT:
               return Expression.count(attribute.attribute);
            case AVG:
               return Expression.avg(attribute.attribute);
            case SUM:
               return Expression.sum(attribute.attribute);
            case MIN:
               return Expression.min(attribute.attribute);
            case MAX:
               return Expression.max(attribute.attribute);
            default:
               throw new RuntimeException("Unknown aggregation function: " + attribute.function);
         }
      }

      @Override
      protected FilterConditionEndContext getEndContext(Attribute attribute) {
         FilterConditionEndContext endContext;
         if (context != null) {
            endContext = context.and().having(attributeToExpression(attribute));
         } else if (builder != null) {
            endContext = builder.having(attributeToExpression(attribute));
         } else {
            endContext = factory.having(attributeToExpression(attribute));
         }
         return endContext;
      }

      @Override
      public QueryBuilder projection(Attribute... attributes) {
         if (builder == null)
            throw new IllegalArgumentException("You have to call projection() on root query builder!");
         Expression[] projections = new Expression[attributes.length];
         for (int i = 0; i < attributes.length; i++) {
            projections[i] = attributeToExpression(attributes[i]);
         }
         builder.select(projections);
         return this;
      }

      @Override
      public QueryBuilder groupBy(String... attributes) {
         if (builder == null)
            throw new IllegalArgumentException("You have to call groupBy() on root query builder!");
         builder.groupBy(attributes);
         context = null; // all further 'having' conditions will start a new .having() context
         return this;
      }

      @Override
      public QueryBuilder orderBy(Attribute attribute, SortOrder order) {
         if (builder == null) throw new IllegalArgumentException("You have to call orderBy() on root query builder!");
         builder.orderBy(attributeToExpression(attribute), order == SortOrder.ASCENDING ?
                  org.infinispan.query.dsl.SortOrder.ASC : org.infinispan.query.dsl.SortOrder.DESC);
         return this;
      }
   }

}
