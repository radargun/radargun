package org.radargun.traits;

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
   Query.Builder getBuilder(String containerName, Class<?> clazz);

   /**
    * Retrieve a reference to the context that should be wrapped in
    * {@link org.radargun.traits.Transactional.Transaction#wrap(Object)}
    * in order to execute the query in transactional context.
    *
    * @param containerName
    * @return Resource associated with the container.
    */
   Query.Context createContext(String containerName);

   /**
    * Makes sure that indexes are in sync with data in the cache.
    * (It is implementation/configuration dependent whether this is necessary)
    */
   void reindex(String containerName);

}
