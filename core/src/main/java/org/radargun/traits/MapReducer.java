package org.radargun.traits;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.Operation;

@Trait(doc = "Provides interface for executing Map/Reduce tasks")
public interface MapReducer<KOut, VOut, R> {
   String TRAIT = MapReducer.class.getSimpleName();
   Operation MAPREDUCE = Operation.register(TRAIT + ".MapReduce");
   Operation MAPREDUCE_COLLATOR = Operation.register(TRAIT + ".MapReduceWithCollator");
   
   interface CollatorTask <R> {
      /**
       * 
       * Execute the CollatorTask
       * 
       * @return the collated result object
       */
      R execute();
   }
   
   interface MapTask <KOut, VOut> {
      /**
       * 
       * Execute the MapTask
       * 
       * @return a Map where each key is an output key and value is reduced value for that output key
       */
      Map<KOut, VOut> execute();
   }
   
   /**
    * 
    * This method executes a MapReduce task against all of the keys in the cache using the specified
    * Mapper and Reducer classes.
    * 
    * @param mapperFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Mapper
    *           implementation. The implementation must have a no argument constructor.
    *
    * @param reducerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    *
    * @param collatorFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Collator
    *           implementation. The implementation must have a no argument constructor.
    * 
    * @return a CollatorTask with the appropriate configuration
    */
   public CollatorTask configureMapReduceTask(String mapperFqn, String reducerFqn, String collatorFqn);

   /**
    * 
    * This method executes a MapReduce task against all of the keys in the cache using the specified
    * Mapper and Reducer classes.
    * 
    * @param mapperFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Mapper
    *           implementation. The implementation must have a no argument constructor.
    *
    * @param reducerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    *
    * @return a Map where each key is an output key and value is reduced value for that output key
    */
   public MapTask configureMapReduceTask(String mapperFqn, String reducerFqn);

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
    * This method allows the caller to provide parameters to the Mapper, Reducer, Combiner, and
    * Collator objects used in a MapReduce job. Each Map contains keys for each public method name,
    * and values for each single String parameter for the method. If no parameters are needed, these
    * can be set to an empty Map.
    * 
    * @param mapperParameters
    *           parameters for the Mapper object
    * @param reducerParameters
    *           parameters for the Reducer object
    * @param combinerParameters
    *           parameters for the Reducer object used as a combiner
    * @param collatorParameters
    *           parameters for the Collator object
    */
   public void setParameters(Map<String, String> mapperParameters, Map<String, String> reducerParameters,
         Map<String, String> combinerParameters, Map<String, String> collatorParameters);

   /**
    * 
    * Specifies a Reducer class to be used with the MapReduceTask during the combine phase
    * 
    * @param combinerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    * @return <code>true</code> if the CacheWrapper supports this flag, else <code>false</code>
    */
   public boolean setCombiner(String combinerFqn);

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
