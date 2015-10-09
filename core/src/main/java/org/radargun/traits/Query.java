package org.radargun.traits;

import java.util.Collection;

/**
 * Non-reusable and non-thread-safe query object.
 */
public interface Query {
    QueryResult execute();

    /**
     * Data retrieved by the query
     */
    interface QueryResult {
        int size();
        Collection values();
    }
}
