package org.radargun.traits;

import java.util.Collection;

/**
 * Non-reusable and non-thread-safe query object.
 */
public interface Query {
    /**
     * Invoke the query on given resource
     * @return
     * @param context
     */
    Result execute(Context context);

    enum SortOrder {
       ASCENDING,
       DESCENDING
    }

    /**
     * Data retrieved by the query
     */
    interface Result {
        int size();
        Collection values();
    }

    /**
     * Context created in {@link Queryable#createContext(String)} and passed to {@link Query#execute(Context)}.
     * Can be transactionally wrapped in the meantime.
     */
    interface Context extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * The instance should be reusable, but not thread-safe.
     */
    interface Builder {
       Builder subquery();
       Builder eq(String attribute, Object value);
       Builder lt(String attribute, Object value);
       Builder le(String attribute, Object value);
       Builder gt(String attribute, Object value);
       Builder ge(String attribute, Object value);
       Builder between(String attribute, Object lowerBound, boolean lowerInclusive, Object upperBound, boolean upperInclusive);
       Builder isNull(String attribute);
       Builder like(String attribute, String pattern);
       Builder contains(String attribute, Object value);
       Builder not(Builder subquery);
       Builder any(Builder... subqueries);
       Builder orderBy(String attribute, SortOrder order);
       Builder projection(String... attribute);
       Builder offset(long offset);
       Builder limit(long limit);
       Query build();
    }
}
