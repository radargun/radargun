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
    */
   interface QueryBuilder {
      QueryBuilder subquery();
      QueryBuilder eq(String attribute, Object value);
      QueryBuilder lt(String attribute, Object value);
      QueryBuilder le(String attribute, Object value);
      QueryBuilder gt(String attribute, Object value);
      QueryBuilder ge(String attribute, Object value);
      QueryBuilder between(String attribute, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive);
      QueryBuilder isNull(String attribute);
      QueryBuilder like(String attribute, String pattern);
      QueryBuilder contains(String attribute, Object value);
      QueryBuilder not(QueryBuilder subquery);
      QueryBuilder any(QueryBuilder... subqueries);
      QueryBuilder orderBy(String attribute, SortOrder order);
      QueryBuilder projection(String... attribute);
      QueryBuilder offset(long offset);
      QueryBuilder limit(long limit);
      Query build();
   }

   enum SortOrder {
      ASCENDING,
      DESCENDING
   }

}
