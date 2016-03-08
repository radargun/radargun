package org.radargun.traits;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.Operation;

/*
 * TODO: create documentation how M/R tasks should work at all; now we use Infinispan model
 * as this is the only implementation, but we should generalize it here (as it's far from obvious).
 */
@Trait(doc = "Provides interface for executing Map/Reduce tasks")
public interface MapReducer<KOut, VOut, R> {
   String TRAIT = MapReducer.class.getSimpleName();
   Operation MAPREDUCE = Operation.register(TRAIT + ".MapReduce");
   Operation MAPREDUCE_COLLATOR = Operation.register(TRAIT + ".MapReduceWithCollator");

   interface Builder<KOut, VOut, R> {
      /**
       *
       * This boolean determines if the Reduce phase of the MapReduceTask is distributed
       *
       * @param distributedReducePhase
       *           if true this task will use distributed reduce phase execution
       * @return this builder instance
       * @throws {@link java.lang.UnsupportedOperationException} if {@link #supportsDistributedReducePhase()} returns false
       */
      Builder<KOut, VOut, R> distributedReducePhase(boolean distributedReducePhase);

      /**
       *
       * This boolean determines if intermediate results of the MapReduceTask are shared
       *
       * @param useIntermediateSharedCache
       *           if true this tasks will share intermediate value cache with other executing
       *           MapReduceTasks on the grid. Otherwise, if false, this task will use its own
       *           dedicated cache for intermediate values
       * @return this builder instance
       * @throws {@link java.lang.UnsupportedOperationException} if {@link #supportsIntermediateSharedCache()} returns false
       */
      Builder<KOut, VOut, R> useIntermediateSharedCache(boolean useIntermediateSharedCache);

      /**
       *
       * Set a timeout for the communication between the nodes during a Map Reduce task. Setting this
       * value to zero or less than zero means to wait forever.
       *
       * @param timeout
       *           the value of the timeout
       * @param unit
       *           the unit of the timeout value
       * @return this builder instance
       * @throws {@link java.lang.UnsupportedOperationException} if {@link #supportsTimeout()} returns false
       */
      Builder<KOut, VOut, R> timeout(long timeout, TimeUnit unit);


      /**
       *
       * Set the name of the result cache. If the service supports it, then the results of the
       * <code>executeMapReduceTask</code> mthod will be stored in this cache.
       *
       * @param resultCacheName
       * @return this builder instance
       * @throws {@link java.lang.UnsupportedOperationException} if {@link #supportsResultCacheName()} returns false
       */
      Builder<KOut, VOut, R> resultCacheName(String resultCacheName);

      /**
       * @param mapperFqn
       *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Mapper
       *           implementation. The implementation must have a no argument constructor.
       * @param mapperParameters
       *           parameters for the Mapper object
       * @return this builder instance
       */
      Builder<KOut, VOut, R> mapper(String mapperFqn, Map<String, String> mapperParameters);

      /**
       * @param reducerFqn
       *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
       *           implementation. The implementation must have a no argument constructor.
       *
       * @param reducerParameters
       *           parameters for the Reducer object
       * @return this builder instance
       */
      Builder<KOut, VOut, R> reducer(String reducerFqn, Map<String, String> reducerParameters);

      /**
       *
       * Specifies a Reducer class to be used with the MapReduceTask during the combine phase
       *
       * @param combinerFqn
       *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
       *           implementation. The implementation must have a no argument constructor.
       * @param combinerParameters
       *           parameters for the Reducer object used as a combiner
       * @return this builder instance
       * @throws {@link java.lang.UnsupportedOperationException} if {@link #supportsCombiner()} returns false
       */
      Builder<KOut, VOut, R> combiner(String combinerFqn, Map<String, String> combinerParameters);

      /**
       *
       * Set the specified collator on the supplied MapTask
       *
       * @param collatorFqn
       *           the fully qualified class name for the Collator implementation. The
       *           implementation must have a no argument constructor.
       * @param collatorParameters
       *           parameters for the Collator object
       */
      Builder<KOut, VOut, R> collator(String collatorFqn, Map<String, String> collatorParameters);

      /**
       * @return The task to be executed
       */
      Task<KOut, VOut, R> build();
   }

   interface Task<KOut, VOut, R> {
      /**
       *
       * Execute the MapTask against all of the keys in the cache
       *
       * @return a Map where each key is an output key and value is reduced value for that output
       *         key
       */
      Map<KOut, VOut> execute();

      /**
       *
       * Execute the MapTask against all of the keys in the cache and use a Collator on the results
       *
       * @return the collated result object
       */
      R executeWithCollator();
   }

   /**
    * Create a new builder that will start a task on given cache.
    * @param cacheName
    * @return the builder instance
    */
   Builder<KOut, VOut, R> builder(String cacheName);

   /**
    * @return <code>true</code> if the trait supports setting the result cache name, else <code>false</code>
    */
   boolean supportsResultCacheName();

   /**
    * @return <code>true</code> if the trait supports intermediate cache, else <code>false</code>
    */
   boolean supportsIntermediateSharedCache();

   /**
    * @return <code>true</code> if the trait supports using combiner, else <code>false</code>
    */
   boolean supportsCombiner();

   /**
    * @return <code>true</code> if the trait supports setting the timeout, else <code>false</code>
    */
   boolean supportsTimeout();

   /**
    * @return <code>true</code> if the trait supports distributed reduce, else <code>false</code>
    */
   boolean supportsDistributedReducePhase();

}
