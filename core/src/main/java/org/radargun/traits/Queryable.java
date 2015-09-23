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
      QueryBuilder eq(Attribute attribute, Object value);
      QueryBuilder lt(Attribute attribute, Object value);
      QueryBuilder le(Attribute attribute, Object value);
      QueryBuilder gt(Attribute attribute, Object value);
      QueryBuilder ge(Attribute attribute, Object value);
      QueryBuilder between(Attribute Attribute, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive);
      QueryBuilder isNull(Attribute attribute);
      QueryBuilder like(Attribute attribute, String pattern);
      QueryBuilder contains(Attribute attribute, Object value);
      QueryBuilder not(QueryBuilder subquery);
      QueryBuilder any(QueryBuilder... subqueries);
      QueryBuilder orderBy(Attribute attribute, SortOrder order);
      QueryBuilder projection(Attribute... attribute);
      QueryBuilder groupBy(String[] attribute);
      QueryBuilder offset(long offset);
      QueryBuilder limit(long limit);
      Query build();
   }

   class Attribute {
      public String attribute;
      public AggregationFunction function;

      public Attribute(String attribute) {
         this(attribute, AggregationFunction.NONE);
      }

      public Attribute(String attribute, AggregationFunction function) {
         this.attribute = attribute;
         this.function = function;
      }
   }

   enum SortOrder {
      ASCENDING,
      DESCENDING
   }

   enum AggregationFunction {
      NONE,
      COUNT,
      SUM,
      AVG,
      MIN,
      MAX;

      public static AggregationFunction parseFunction(String string) {
         AggregationFunction result;
         if (string.equalsIgnoreCase("COUNT")) {
            result = Queryable.AggregationFunction.COUNT;
         } else if (string.equalsIgnoreCase("AVG")) {
            result = Queryable.AggregationFunction.AVG;
         } else if (string.equalsIgnoreCase("SUM")) {
            result = Queryable.AggregationFunction.SUM;
         } else if (string.equalsIgnoreCase("MIN")) {
            result = Queryable.AggregationFunction.MIN;
         } else if (string.equalsIgnoreCase("MAX")) {
            result = Queryable.AggregationFunction.MAX;
         } else {
            throw new IllegalArgumentException("Aggregation function not recognized: " + string);
         }
         return result;
      }
   }

   /**
    * Non-reusable and non-thread-safe query object.
    */
   interface Query {
      QueryResult execute();
   }

   /**
    * Data retrieved by the query
    */
   interface QueryResult {
      int size();
      Collection values();
   }
}
