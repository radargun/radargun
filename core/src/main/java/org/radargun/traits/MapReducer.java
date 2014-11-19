package org.radargun.traits;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.Operation;

@Trait(doc = "Provides interface for executing Map/Reduce tasks")
public interface MapReducer<KOut, VOut, R> {
   String TRAIT = MapReducer.class.getSimpleName();
   Operation MAPREDUCE = Operation.register(TRAIT + ".MapReduce");
   Operation MAPREDUCE_COLLATOR = Operation.register(TRAIT + ".MapReduceWithCollator");

   interface MapTask<KOut, VOut, R> {
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
       * Set the specified collator on the supplied MapTask
       * 
       * @param collatorFqn
       *           the fully qualified class name for the Collator implementation. The
       *           implementation must have a no argument constructor.
       * @param collatorParameters
       *           parameters for the Collator object
       */
      public void setCollatorInstance(String collatorFqn,
            Map<String, String> collatorParameters);

      /**
       * 
       * Execute the MapTask against all of the keys in the cache and use a Collator on the results
       * 
       * @return the collated result object
       */
      R executeWithCollator();

   }

   /**
    * 
    * Configure a CollatorTask using the specified Mapper and Reducer classes.
    * 
    * @param mapperFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Mapper
    *           implementation. The implementation must have a no argument constructor.
    * 
    * @param mapperParameters
    *           parameters for the Mapper object
    *
    * @param reducerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    *
    * @param reducerParameters
    *           parameters for the Reducer object
    *
    * @return a Map where each key is an output key and value is reduced value for that output key
    */
   public MapTask<KOut, VOut, R> configureMapReduceTask(String mapperFqn, Map<String, String> mapperParameters,
         String reducerFqn, Map<String, String> reducerParameters);

   /**
    * 
    * This boolean determines if the Reduce phase of the MapReduceTask is distributed
    * 
    * @param distributeReducePhase
    *           if true this task will use distributed reduce phase execution
    * @return <code>true</code> if the CacheWrapper supports this flag, else <code>false</code>
    */
   public boolean setDistributeReducePhase(boolean distributeReducePhase);

   /**
    * 
    * This boolean determines if intermediate results of the MapReduceTask are shared
    * 
    * @param useIntermediateSharedCache
    *           if true this tasks will share intermediate value cache with other executing
    *           MapReduceTasks on the grid. Otherwise, if false, this task will use its own
    *           dedicated cache for intermediate values
    * @return <code>true</code> if the CacheWrapper supports this flag, else <code>false</code>
    */
   public boolean setUseIntermediateSharedCache(boolean useIntermediateSharedCache);

   /**
    * 
    * Set a timeout for the communication between the nodes during a Map Reduce task. Setting this
    * value to zero or less than zero means to wait forever.
    * 
    * @param timeout
    *           the value of the timeout
    * @param unit
    *           the unit of the timeout value
    * @return <code>true</code> if the CacheWrapper supports setting the timeout, else
    *         <code>false</code>
    */
   public boolean setTimeout(long timeout, TimeUnit unit);

   /**
    * 
    * Specifies a Reducer class to be used with the MapReduceTask during the combine phase
    * 
    * @param combinerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    * @param combinerParameters
    *           parameters for the Reducer object used as a combiner
    * @return <code>true</code> if the CacheWrapper supports this flag, else <code>false</code>
    */
   public boolean setCombiner(String combinerFqn, Map<String, String> combinerParameters);

   /**
    * 
    * Set the name of the result cache. If the service supports it, then the results of the
    * <code>executeMapReduceTask</code> mthod will be stored in this cache.
    * 
    * @param resultCacheName
    * @return <code>true</code> if the setting supports setting the result cache name, else
    *         <code>false</code>
    */
   public boolean setResultCacheName(String resultCacheName);

}
