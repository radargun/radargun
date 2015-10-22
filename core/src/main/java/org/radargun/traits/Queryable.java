package org.radargun.traits;

import java.util.Collection;

import org.radargun.Operation;

/**
 * @author Anna Manukyan
 */
@Trait(doc = "Allows running queries on the node.")
public interface Queryable {
   Operation QUERY = Operation.register(Queryable.class.getSimpleName() + ".Query");
   Operation REINDEX = Operation.register(Queryable.class.getSimpleName() + ".Reindex");

   /**
    * Get object for building the query.
    * @param containerName Name of the container (cache, database, ...) where the query should be executed.
    * @return Builder
    */
   QueryBuilder getBuilder(String containerName, Class<?> clazz);

   /**
    * Makes sure that indexes are in sync with data in the cache.
    * (It is implementation/configuration dependent whether this is necessary)
    */
   void reindex(String containerName);

   /**
    * The instance should be reusable, but not thread-safe.
    * Conditions defined after groupBy call are meant to be part of the HAVING clause.
    */
   interface QueryBuilder {
      QueryBuilder subquery();
      QueryBuilder eq(SelectExpression selectExpression, Object value);
      QueryBuilder lt(SelectExpression selectExpression, Object value);
      QueryBuilder le(SelectExpression selectExpression, Object value);
      QueryBuilder gt(SelectExpression selectExpression, Object value);
      QueryBuilder ge(SelectExpression selectExpression, Object value);
      QueryBuilder between(SelectExpression selectExpression, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive);
      QueryBuilder isNull(SelectExpression selectExpression);
      QueryBuilder like(SelectExpression selectExpression, String pattern);
      QueryBuilder contains(SelectExpression selectExpression, Object value);
      QueryBuilder not(QueryBuilder subquery);
      QueryBuilder any(QueryBuilder... subqueries);
      QueryBuilder orderBy(SelectExpression selectExpression);
      QueryBuilder projection(SelectExpression... selectExpressions);
      QueryBuilder groupBy(String[] attribute);
      QueryBuilder offset(long offset);
      QueryBuilder limit(long limit);
      Query build();
   }

   /**
    * Used to represent aggregated attributes and also order by expressions
    */
   class SelectExpression {
      public String attribute;
      public AggregationFunction function;
      public boolean asc;

      public SelectExpression(String attribute) {
         this(attribute, AggregationFunction.NONE, true);
      }

      public SelectExpression(String attribute, AggregationFunction function) {
         this(attribute, function, true);
      }

      public SelectExpression(String attribute, boolean asc) {
         this(attribute, AggregationFunction.NONE, asc);
      }

      public SelectExpression(String attribute, AggregationFunction function, boolean asc) {
         this.attribute = attribute;
         this.function = function;
         this.asc = asc;
      }
   }

   enum AggregationFunction {
      NONE,
      COUNT,
      SUM,
      AVG,
      MIN,
      MAX;
   }

}
