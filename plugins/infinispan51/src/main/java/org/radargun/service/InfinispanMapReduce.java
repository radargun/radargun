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

   protected Map<String, String> mapperParameters;
   protected Map<String, String> reducerParameters;
   protected Map<String, String> combinerParameters;
   protected Map<String, String> collatorParameters;

   protected String combinerFqn = null;

   protected String resultCacheName = null;

   protected boolean printResult = false;

   protected Collator<KOut, VOut, R> collator = null;

   public InfinispanMapReduce(Infinispan51EmbeddedService service) {
      this.service = service;
   }

   public void setPrintResult(boolean printResult) {
      this.printResult = printResult;
   }
   
   class InfinispanMapReduceCollatorTask implements CollatorTask<R> {
      MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask;
      

      public InfinispanMapReduceCollatorTask(MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask) {
         this.mapReduceTask = mapReduceTask;
      }

      @Override
      public R execute() {
         return mapReduceTask.execute(collator);
      }
      
   }

   class InfinispanMapReduceTask implements MapTask<KOut, VOut> {
      MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask;
      

      public InfinispanMapReduceTask(MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask) {
         this.mapReduceTask = mapReduceTask;
      }

      @Override
      public Map<KOut, VOut> execute() {
         Map<KOut, VOut> result = mapReduceTask.execute();
         if (resultCacheName != null) {
            Cache<Object, Object> resultCache = service.getCache(resultCacheName);
            for (Map.Entry<KOut, VOut> entry : result.entrySet()) {
               if (printResult) {
                  log.info("key: " + entry.getKey() + " value: " + entry.getValue());
               }
               resultCache.put(entry.getKey(), entry.getValue());
            }
         }
         return result;
      }
      
   }

   @Override
   public CollatorTask configureMapReduceTask(String mapperFqn, String reducerFqn, String collatorFqn) {
      MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask = mapReduceTaskFactory();

      Mapper<KIn, VIn, KOut, VOut> mapper = null;
      Reducer<KOut, VOut> reducer = null;

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      try {
         mapper = Utils.instantiate(classLoader, mapperFqn);
         mapReduceTask = mapReduceTask.mappedWith(mapper);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e));
      }

      try {
         reducer = Utils.instantiate(classLoader, reducerFqn);
         mapReduceTask = mapReduceTask.reducedWith(reducer);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e));
      }

      setCombiner(mapReduceTask, combinerFqn);

      try {
         collator = Utils.instantiate(classLoader, collatorFqn);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Collator class: " + collatorFqn, e));
      }

      if (mapper != null && reducer != null && collator != null) {
         Utils.invokeMethodWithString(mapper, this.mapperParameters);
         Utils.invokeMethodWithString(reducer, this.reducerParameters);
         Utils.invokeMethodWithString(collator, this.collatorParameters);
      }
      
      return new InfinispanMapReduceCollatorTask(mapReduceTask);
   }

   @Override
   public MapTask configureMapReduceTask(String mapperFqn, String reducerFqn) {
      MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask = mapReduceTaskFactory();

      Mapper<KIn, VIn, KOut, VOut> mapper = null;
      Reducer<KOut, VOut> reducer = null;

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      try {
         mapper = Utils.instantiate(classLoader, mapperFqn);
         mapReduceTask = mapReduceTask.mappedWith(mapper);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e));
      }

      try {
         reducer = Utils.instantiate(classLoader, reducerFqn);
         mapReduceTask = mapReduceTask.reducedWith(reducer);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e));
      }

      if (mapper != null && reducer != null) {
         Utils.invokeMethodWithString(mapper, this.mapperParameters);
         Utils.invokeMethodWithString(reducer, this.reducerParameters);
      }
      
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
   public void setParameters(Map<String, String> mapperParameters, Map<String, String> reducerParameters,
         Map<String, String> combinerParameters, Map<String, String> collatorParameters) {
      this.mapperParameters = mapperParameters;
      this.reducerParameters = reducerParameters;
      this.combinerParameters = combinerParameters;
      this.collatorParameters = collatorParameters;
   }

   @Override
   public boolean setCombiner(String combinerFqn) {
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
    * @param combinerFqn
    *           the fully qualified class name for the org.infinispan.distexec.mapreduce.Reducer
    *           implementation. The implementation must have a no argument constructor.
    *
    * @return the MapReduceTask with the combiner set if the CacheWrapper supports it
    */
   protected MapReduceTask<KIn, VIn, KOut, VOut> setCombiner(MapReduceTask<KIn, VIn, KOut, VOut> task,
         String combinerFqn) {
      return task;
   }

}
