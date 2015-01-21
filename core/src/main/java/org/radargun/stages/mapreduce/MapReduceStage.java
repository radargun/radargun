package org.radargun.stages.mapreduce;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.RandomDataStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.DataOperationStats;
import org.radargun.stats.DefaultStatistics;
import org.radargun.stats.Statistics;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.Clustered;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.MapReducer;
import org.radargun.traits.MapReducer.Task;
import org.radargun.utils.Projections;
import org.radargun.utils.Utils;

/**
 * Executes a MapReduce Task against the cache.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stage which executes a MapReduce Task against all keys in the cache.")
public class MapReduceStage<KOut, VOut, R> extends AbstractDistStage {

   public static final String MAPREDUCE_RESULT_KEY = "mapreduceResult";

   @Property(doc = "Name of the cache on which should be the map-reduce executed. Default is the default cache.")
   private String cacheName;

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "mapper implementation to execute.")
   private String mapperFqn;

   @Property(optional = true, doc = "A String in the form of "
         + "'methodName:methodParameter;methodName1:methodParameter1' that allows"
         + " invoking a method on the Mapper Object. The method"
         + " must be public and take a String parameter. The default is null.")
   private String mapperParams = null;

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Reducer implementation to execute.")
   private String reducerFqn;

   @Property(optional = true, doc = "A String in the form of "
         + "'methodName:methodParameter;methodName1:methodParameter1' that allows"
         + " invoking a method on the Reducer Object. The method"
         + " must be public and take a String parameter. The default is null.")
   private String reducerParams = null;

   @Property(optional = true, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Reducer implementation to use as a combiner.")
   private String combinerFqn;

   @Property(optional = true, doc = "A String in the form of "
         + "'methodName:methodParameter;methodName1:methodParameter1' that allows"
         + " invoking a method on the Reducer Object used as a combiner. The method"
         + " must be public and take a String parameter. The default is null.")
   private String combinerParams = null;

   @Property(optional = true, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Collator implementation to execute. The default is null.")
   private String collatorFqn = null;

   @Property(optional = true, doc = "A String in the form of "
         + "'methodName:methodParameter;methodName1:methodParameter1' that allows"
         + " invoking a method on the Collator Object. The method"
         + " must be public and take a String parameter. The default is null.")
   private String collatorParams = null;

   @Property(optional = true, doc = "Boolean value that determines if the "
         + "Reduce phase of the MapReduceTask is distributed. The default is true.")
   private boolean distributeReducePhase = true;

   @Property(optional = true, doc = "Boolean value that determines if the "
         + "intermediate results of the MapReduceTask are shared. The default is true.")
   private boolean useIntermediateSharedCache = true;

   @Property(optional = true, doc = "Boolean value that determines if the "
         + "final results of the MapReduceTask are stored in the cache. "
         + "The collated object will be stored at key MAPREDUCE_RESULT_KEY. "
         + "The result map will be stored in a cache named MAPREDUCE_RESULT_KEY. The default is false.")
   private boolean storeResultInCache = false;

   @Property(optional = true, doc = "Boolean value that determines if the "
         + "final results of the MapReduceTask are written to the log of the "
         + "first slave node. The default is false.")
   private boolean printResult = false;

   @Property(doc = "A timeout value for the remote communication that happens "
         + "during a Map/Reduce task. The default is zero which means to wait forever.")
   private long timeout = 0;

   @Property(doc = "The java.util.concurrent.TimeUnit to use with the timeout "
         + "property. The default is TimeUnit.MILLISECONDS.")
   private TimeUnit unit = TimeUnit.MILLISECONDS;

   @Property(doc = "The number of times to execute the Map/Reduce task. The default is 10.")
   private int numExecutions = 10;

   private Map<KOut, VOut> payloadMap = null;
   private R payloadObject = null;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private MapReducer<KOut, VOut, R> mapReducer;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Clustered clustered;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private CacheInformation cacheInformation;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   private boolean supportsResultCacheName = false;

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError())
         return result;

      Report report = masterState.getReport();
      Report.Test test = report.createTest("Map_Reduce_Stage", null, true);
      int testIteration = test.getIterations().size();

      Map<Integer, Report.SlaveResult> numberOfResultKeysResult = new HashMap<Integer, Report.SlaveResult>();
      Map<Integer, Report.SlaveResult> durationsResult = new HashMap<Integer, Report.SlaveResult>();

      for (MapReduceAck ack : Projections.instancesOf(acks, MapReduceAck.class)) {
         if (ack.stats != null) {
            DataOperationStats opStats;
            if (ack.stats.getOperationsStats().containsKey(MapReducer.MAPREDUCE.name)) {
               opStats = (DataOperationStats) ack.stats.getOperationsStats().get(MapReducer.MAPREDUCE.name);
            } else {
               opStats = (DataOperationStats) ack.stats.getOperationsStats().get(MapReducer.MAPREDUCE_COLLATOR.name);
            }
            opStats.setTotalBytes((Long) masterState.get(RandomDataStage.RANDOMDATA_TOTALBYTES_KEY));
            test.addStatistics(testIteration, ack.getSlaveIndex(), Collections.singletonList(ack.stats));
            durationsResult.put(ack.getSlaveIndex(), new Report.SlaveResult(opStats.getResponseTimes(), false));
            test.addResult(testIteration, new Report.TestResult("Map/Reduce durations result map", durationsResult,
                  "-", false));
         }
         if (ack.numberOfResultKeys != null) {
            numberOfResultKeysResult.put(ack.getSlaveIndex(), new Report.SlaveResult(ack.numberOfResultKeys, false));
            test.addResult(testIteration, new Report.TestResult("Key count in Map/Reduce result map",
                  numberOfResultKeysResult, "-", false));
         }
      }

      return StageResult.SUCCESS;
   }

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         return errorResponse("Service is not runnning", null);
      }

      if (mapperFqn == null || reducerFqn == null) {
         return errorResponse("Both the mapper and reducer class must be specified.", null);
      }

      if (slaveState.getSlaveIndex() == 0) {
         DistStageAck result = null;
         Map<KOut, VOut> prevPayloadMap = null;
         R prevPayloadObject = null;

         Statistics stats = new DefaultStatistics(new DataOperationStats());
         Task<KOut, VOut, R> mrTask = configureMapReduceTask();

         stats.begin();
         for (int i = 0; i < numExecutions; i++) {
            prevPayloadMap = payloadMap;
            prevPayloadObject = payloadObject;
            result = executeMapReduceTask(mrTask, stats, supportsResultCacheName);

            if (prevPayloadMap != null) {
               if (prevPayloadMap.keySet().equals(payloadMap.keySet())
                     && prevPayloadMap.entrySet().equals(payloadMap.entrySet())) {
                  log.info(i + ": Got the same results for two Map/Reduce runs");
               } else {
                  log.error(i + ": Did not get the same results for two Map/Reduce runs");
                  break;
               }
            }

            if (prevPayloadObject != null) {
               if (prevPayloadObject.equals(payloadObject)) {
                  log.info(i + ": Got the same results for two Map/Reduce runs with collator");
               } else {
                  log.error(i + ": Did not get the same results for two Map/Reduce runs with collator");
                  break;
               }
            }
            if (result.isError() && exitOnFailure) {
               break;
            }
         }
         stats.end();
         return result;
      } else {
         return new MapReduceAck(slaveState);
      }
   }

   private Task<KOut, VOut, R> configureMapReduceTask() {
      MapReducer.Builder<KOut, VOut, R> builder = mapReducer.builder(cacheName)
            .mapper(mapperFqn, Utils.parseParams(mapperParams))
            .reducer(reducerFqn, Utils.parseParams(reducerParams));

      log.info("--------------------");
      String mapReducerName = mapReducer.getClass().getName();
      if (mapReducer.supportsDistributedReducePhase()) {
         log.info(mapReducerName + " supports MapReducer.setDistributeReducePhase()");
         builder.distributedReducePhase(distributeReducePhase);
      } else {
         log.info(mapReducerName + " does not support MapReducer.setDistributeReducePhase()");
      }
      if (mapReducer.supportsIntermediateSharedCache()) {
         log.info(mapReducerName + " supports MapReducer.setUseIntermediateSharedCache()");
         builder.useIntermediateSharedCache(useIntermediateSharedCache);
      } else {
         log.info(mapReducerName + " does not support MapReducer.setUseIntermediateSharedCache()");
      }
      if (mapReducer.supportsTimeout()) {
         log.info(mapReducerName + " supports MapReducer.setTimeout()");
         builder.timeout(timeout, unit);
      } else {
         log.info(mapReducerName + " does not support MapReducer.setTimeout()");
      }
      if (mapReducer.supportsCombiner()) {
         log.info(mapReducerName + " supports MapReducer.setCombiner()");
         if (combinerFqn != null) {
            builder.combiner(combinerFqn, Utils.parseParams(combinerParams));
         }
      } else {
         log.info(mapReducerName + " does not support MapReducer.setCombiner()");
      }
      if (collatorFqn != null) {
         builder.collator(collatorFqn, Utils.parseParams(collatorParams));
      } else {
         // If the MapReducer doesn't support storing the results in the cache, then do it manually.
         if (storeResultInCache) {
            supportsResultCacheName = mapReducer.supportsResultCacheName();
            if (supportsResultCacheName) {
               log.info(mapReducerName + " supports MapReducer.setResultCacheName()");
               builder.resultCacheName(MAPREDUCE_RESULT_KEY);
            } else {
               log.info(mapReducerName + " does not support MapReducer.setResultCacheName()");
            }
         }
      }

      return builder.build();
   }

   private DistStageAck executeMapReduceTask(Task<KOut, VOut, R> task, Statistics stats,
         boolean supportsResultCacheName) {
      long durationNanos;
      long start;

      MapReduceAck ack = new MapReduceAck(slaveState);
      try {
         if (collatorFqn != null) {
            start = System.nanoTime();
            payloadObject = task.executeWithCollator();
            durationNanos = System.nanoTime() - start;
            stats.registerRequest(durationNanos, MapReducer.MAPREDUCE_COLLATOR);
            log.info("MapReduce task with Collator completed in "
                  + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
            ack.setStats(stats);
            if (printResult) {
               log.info("MapReduce result: " + payloadObject.toString());
            }
            if (storeResultInCache) {
               try {
                  basicOperations.getCache(cacheName).put(MAPREDUCE_RESULT_KEY, payloadObject);
               } catch (Exception e) {
                  log.error("Failed to put collated result object into cache", e);
               }
            }
         } else {
            start = System.nanoTime();
            payloadMap = task.execute();
            durationNanos = System.nanoTime() - start;
            stats.registerRequest(durationNanos, MapReducer.MAPREDUCE);

            if (payloadMap != null) {
               log.info("MapReduce task completed in " + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
               log.info("Result map contains '" + payloadMap.keySet().size() + "' keys.");
               ack.setNumberOfResultKeys(payloadMap.keySet().size());
               ack.setStats(stats);

               // Store results in the cache
               if (storeResultInCache && !supportsResultCacheName) {
                  BasicOperations.Cache<Object, Object> resultCache = basicOperations.getCache(MAPREDUCE_RESULT_KEY);
                  if (printResult) {
                     log.info("MapReduce result:");
                  }
                  for (Map.Entry<KOut, VOut> entry : payloadMap.entrySet()) {
                     if (printResult) {
                        log.info("key: " + entry.getKey() + " value: " + entry.getValue());
                     }
                     resultCache.put(entry.getKey(), entry.getValue());
                  }
               } else {
                  if (printResult) {
                     log.info("MapReduce result:");
                     for (Map.Entry<KOut, VOut> entry : payloadMap.entrySet()) {
                        log.info("key: " + entry.getKey() + " value: " + entry.getValue());
                     }
                  }
               }
            } else {
               if (storeResultInCache) {
                  log.info("MapReduce task completed in " + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
                  log.info("Result map contains '" + cacheInformation.getCache(MAPREDUCE_RESULT_KEY).getTotalSize()
                        + "' keys.");
                  ack.setNumberOfResultKeys(cacheInformation.getCache(MAPREDUCE_RESULT_KEY).getTotalSize());
                  ack.setStats(stats);
               } else {
                  ack.setStats(stats);
                  ack.error("executeMapReduceTask() returned null");
               }
            }
         }
      } catch (Exception e) {
         ack.error("executeMapReduceTask() threw an exception", e);
         log.error("executeMapReduceTask() returned an exception", e);
      }
      log.infof("%d nodes were used. %d entries on this node", clustered.getClusteredNodes(), cacheInformation
            .getCache(cacheName).getLocallyStoredSize());
      log.info("--------------------");

      return ack;
   }

   private static class MapReduceAck extends DistStageAck {
      private Statistics stats;
      private String numberOfResultKeys;

      private MapReduceAck(SlaveState slaveState) {
         super(slaveState);
      }

      public void setStats(Statistics stats) {
         this.stats = stats;
      }

      public void setNumberOfResultKeys(long numberOfResultKeys) {
         this.numberOfResultKeys = Long.toString(numberOfResultKeys);
      }
   }

}
