package org.radargun.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.Collator;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Mapper;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.MapReducer;
import org.radargun.utils.Utils;

public class InfinispanMapReduce<KIn, VIn, KOut, VOut, R> implements MapReducer<KOut, VOut, R> {
   protected Log log = LogFactory.getLog(getClass());

   protected Infinispan51EmbeddedService service;

   protected boolean distributeReducePhase;
   protected boolean useIntermediateSharedCache;
   protected long timeout = 0;
   protected TimeUnit unit = TimeUnit.MILLISECONDS;

   protected String combinerFqn = null;
   protected Map<String, String> combinerParameters;

   protected String resultCacheName = null;

   protected boolean printResult = false;

   public InfinispanMapReduce(Infinispan51EmbeddedService service) {
      this.service = service;
   }

   public void setPrintResult(boolean printResult) {
      this.printResult = printResult;
   }

   class InfinispanMapReduceTask implements MapTask<KOut, VOut, R> {
      MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask;
      Collator<KOut, VOut, R> collator = null;

      public InfinispanMapReduceTask(MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask) {
         this.mapReduceTask = mapReduceTask;
      }

      @Override
      public Map<KOut, VOut> execute() {
         return mapReduceTask.execute();
      }

      @Override
      public void setCollatorInstance(String collatorFqn, Map<String, String> collatorParameters) {

         ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

         collator = null;
         try {
            collator = Utils.instantiate(classLoader, collatorFqn);
            Utils.invokeMethodWithString(collator, collatorParameters);
         } catch (Exception e) {
            throw (new IllegalArgumentException("Could not instantiate Collator class: " + collatorFqn, e));
         }

      }

      @Override
      public R executeWithCollator() {
         return mapReduceTask.execute(collator);
      }

   }

   @Override
   public MapTask<KOut, VOut, R> configureMapReduceTask(String mapperFqn, Map<String, String> mapperParameters,
         String reducerFqn, Map<String, String> reducerParameters) {
      MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask = mapReduceTaskFactory();

      Mapper<KIn, VIn, KOut, VOut> mapper = null;
      Reducer<KOut, VOut> reducer = null;

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      try {
         mapper = Utils.instantiate(classLoader, mapperFqn);
         Utils.invokeMethodWithString(mapper, mapperParameters);
         mapReduceTask = mapReduceTask.mappedWith(mapper);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e));
      }

      try {
         reducer = Utils.instantiate(classLoader, reducerFqn);
         Utils.invokeMethodWithString(reducer, reducerParameters);
         mapReduceTask = mapReduceTask.reducedWith(reducer);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e));
      }

      mapReduceTask = setCombiner(mapReduceTask);

      return new InfinispanMapReduceTask(mapReduceTask);
   }

   @Override
   public boolean setResultCacheName(String resultCacheName) {
      this.resultCacheName = resultCacheName;
      // Add the result cache to the list of caches known by the service 
      service.caches.put(resultCacheName, service.cacheManager.getCache(resultCacheName, true));
      return true;
   }

   @Override
   public boolean setDistributeReducePhase(boolean distributeReducePhase) {
      return false;
   }

   @Override
   public boolean setUseIntermediateSharedCache(boolean useIntermediateSharedCache) {
      return false;
   }

   @Override
   public boolean setTimeout(long timeout, TimeUnit unit) {
      return false;
   }

   @Override
   public boolean setCombiner(String combinerFqn, Map<String, String> combinerParameters) {
      return false;
   }

   /**
    * 
    * Factory method to create a MapReduceTask class. Infinispan 5.1 executed the reduce phase on a
    * single node. Infinispan 5.2 added the option to distribute the reduce phase and share
    * intermediate results. These options are controlled by the {@link #distributeReducePhase} and
    * {@link #useIntermediateSharedCache} properties.
    * 
    * @return a MapReduceTask object that executes against on the default cache
    */
   protected MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTaskFactory() {
      Cache<KIn, VIn> cache = service.cacheManager.getCache(null);
      return new MapReduceTask<KIn, VIn, KOut, VOut>(cache);
   }

   /**
    * 
    * Method to set the combiner on a MapReduceTask object. Infinispan 5.2 added the option to
    * perform a combine phase on the local node before executing the global reduce phase.
    * 
    * @param task
    *           the MapReduceTask object to modify
    *
    * @return the MapReduceTask with the combiner set if the CacheWrapper supports it
    */
   protected MapReduceTask<KIn, VIn, KOut, VOut> setCombiner(MapReduceTask<KIn, VIn, KOut, VOut> task) {
      return task;
   }

}
