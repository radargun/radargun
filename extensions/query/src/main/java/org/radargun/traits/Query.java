package org.radargun.traits;

import java.util.Collection;

/**
 * Non-reusable and non-thread-safe query object.
 */
public interface Query {
   /**
    * Invoke the query on given resource
    *
    * @param context
    * @return
    */
   Result execute(Context context);

   enum AggregationFunction {
      NONE,
      COUNT,
      SUM,
      AVG,
      MIN,
      MAX;
   }

   /**
    * Data retrieved by the query
    */
   interface Result {
      int size();

      Collection values();
   }

   /**
    * Context created in {@link Queryable#createContext(String)} and passed to {@link Query#execute(Context)}. Can be
    * transactionally wrapped in the meantime.
    */
   interface Context extends AutoCloseable {
      @Override
      void close();
   }

   /**
    * The instance should be reusable, but not thread-safe.
    * Conditions defined after groupBy call are meant to be part of the HAVING clause.
    */
   interface Builder {
      Builder subquery();

      Builder eq(SelectExpression selectExpression, Object value);

      Builder lt(SelectExpression selectExpression, Object value);

      Builder le(SelectExpression selectExpression, Object value);

      Builder gt(SelectExpression selectExpression, Object value);

      Builder ge(SelectExpression selectExpression, Object value);

      Builder between(SelectExpression selectExpression, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive);

      Builder isNull(SelectExpression selectExpression);

      Builder like(SelectExpression selectExpression, String pattern);

      Builder contains(SelectExpression selectExpression, Object value);
      
      Builder not(Builder subquery);

      Builder any(Builder... subqueries);

      Builder orderBy(SelectExpression selectExpression);

      Builder projection(SelectExpression... selectExpressions);

      Builder groupBy(String[] attribute);

      Builder offset(long offset);

      Builder limit(long limit);

      Query build();
   }

   /**
    * Used to represent aggregated attributes and also order by expressions
    */
   class SelectExpression {
      public final String attribute;
      public final AggregationFunction function;
      public final Boolean asc;

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
      
      public String attribute(){
         return attribute;
      }
   }
}
