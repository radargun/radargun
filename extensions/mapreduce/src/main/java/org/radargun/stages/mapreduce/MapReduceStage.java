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
import org.radargun.stats.Request;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.MapReducer;
import org.radargun.traits.MapReducer.Task;
import org.radargun.utils.KeyValueProperty;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.Utils;

/**
 * Executes a MapReduce Task against the cache.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@SuppressWarnings("serial")
@Stage(doc = "Stage which executes a MapReduce Task against all keys in the cache.")
public class MapReduceStage<KOut, VOut, R> extends AbstractDistStage {

   public static final String MAPREDUCE_RESULT_KEY = MapReduceStage.class.getName() + " " + "mapreduceResult";

   @Property(doc = "Name of the source to execute map-reduce task on. Default value is implementation specific.")
   public String sourceName;

   @Property(optional = false, doc = "Fully qualified class name of the " + "mapper implementation to execute.")
   public String mapperFqn;

   @Property(optional = true, doc = "A list of key-value pairs in the form of "
      + "'methodName:methodParameter' that allows"
      + " invoking a method on the Mapper Object. The method"
      + " must be public and take a String parameter. The default is null.",
      complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   public List<KeyValueProperty> mapperParams = null;

   @Property(optional = false, doc = "Fully qualified class name of the "
      + "org.infinispan.distexec.mapreduce.Reducer implementation to execute.")
   public String reducerFqn;

   @Property(optional = true, doc = "A list of key-value pairs in the form of "
      + "'methodName:methodParameter' that allows"
      + " invoking a method on the Reducer Object. The method"
      + " must be public and take a String parameter. The default is null.",
      complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   public List<KeyValueProperty> reducerParams = null;

   @Property(optional = true, doc = "Fully qualified class name of the "
      + "org.infinispan.distexec.mapreduce.Reducer implementation to use as a combiner.")
   public String combinerFqn;

   @Property(optional = true, doc = "A list of key-value pairs in the form of "
      + "'methodName:methodParameter' that allows"
      + " invoking a method on the Reducer Object used as a combiner. The method"
      + " must be public and take a String parameter. The default is null.",
      complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   public List<KeyValueProperty> combinerParams = null;

   @Property(optional = true, doc = "Fully qualified class name of the "
      + "org.infinispan.distexec.mapreduce.Collator implementation to execute. The default is null.")
   public String collatorFqn = null;

   @Property(optional = true, doc = "A list of key-value pairs in the form of "
      + "'methodName:methodParameter' that allows"
      + " invoking a method on the Collator Object. The method"
      + " must be public and take a String parameter. The default is null.",
      complexConverter = KeyValueProperty.KeyValuePairListConverter.class)
   public List<KeyValueProperty> collatorParams = null;

   @Property(optional = true, doc = "Boolean value that determines if the "
      + "final results of the MapReduceTask are stored in the slave state. "
      + "The default is false.")
   public boolean storeResult = false;

   @Property(optional = true, doc = "Boolean value that determines if the "
      + "final results of the MapReduceTask are written to the log of the "
      + "first slave node. The default is false.")
   public boolean printResult = false;

   @Property(doc = "A timeout value for the remote communication that happens "
      + "during a Map/Reduce task. The default is zero which means to wait forever.", converter = TimeConverter.class)
   public long timeout = 0;

   @Property(doc = "The number of times to execute the Map/Reduce task. The default is 10.")
   public int numExecutions = 10;

   @Property(doc = "Compare results of previous executions on entry-by-entry basis. WARNING: This can be lengthy operation " +
         "on data sets with many distinct keys. On false, only result sizes are checked. The default is true.")
   private boolean deepComparePreviousExecutions = true;

   @Property(doc = "The name of the key in the MasterState object that returns the total number of "
      + "bytes processed by the Map/Reduce task. The default is RandomDataStage.RANDOMDATA_TOTALBYTES_KEY.")
   public String totalBytesKey = RandomDataStage.RANDOMDATA_TOTALBYTES_KEY;

   private Map<KOut, VOut> payloadMap = null;
   private R payloadObject = null;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private MapReducer<KOut, VOut, R> mapReducer;

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

      for (MapReduceAck ack : instancesOf(acks, MapReduceAck.class)) {
         if (ack.stats != null) {
            DataOperationStats opStats;
            if (ack.stats.getOperationsStats().containsKey(MapReducer.MAPREDUCE.name)) {
               opStats = (DataOperationStats) ack.stats.getOperationsStats().get(MapReducer.MAPREDUCE.name);
            } else {
               opStats = (DataOperationStats) ack.stats.getOperationsStats().get(MapReducer.MAPREDUCE_COLLATOR.name);
            }
            opStats.setTotalBytes((Long) masterState.get(totalBytesKey));
            test.addStatistics(testIteration, ack.getSlaveIndex(), Collections.singletonList(ack.stats));
            durationsResult.put(ack.getSlaveIndex(), new Report.SlaveResult(opStats.getResponseTimes(), false));
            test.addResult(testIteration, new Report.TestResult("Map/Reduce durations", durationsResult, "", false));
         }
         if (ack.numberOfResultKeys != null) {
            numberOfResultKeysResult.put(ack.getSlaveIndex(), new Report.SlaveResult(ack.numberOfResultKeys, false));
            test.addResult(testIteration, new Report.TestResult("Key count in Map/Reduce result map",
               numberOfResultKeysResult, "", false));
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

      DistStageAck result = null;
      Map<KOut, VOut> prevPayloadMap = null;
      R prevPayloadObject = null;

      Statistics stats = new DefaultStatistics(new DataOperationStats());
      Task<KOut, VOut, R> mrTask = configureMapReduceTask();

      stats.begin();
      for (int i = 0; i < numExecutions; i++) {
         prevPayloadMap = payloadMap;
         prevPayloadObject = payloadObject;

         result = executeMapReduceTask(mrTask, stats);

         if (prevPayloadMap != null) {
            boolean resultsEqual = deepComparePreviousExecutions ? prevPayloadMap.equals(payloadMap) : prevPayloadMap.size() == payloadMap.size();
            if (resultsEqual) {
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

      if (storeResult) {
         try {
            if (collatorFqn == null) {
               slaveState.put(MAPREDUCE_RESULT_KEY, new MapReduceResult<>(payloadMap));
            } else {
               slaveState.put(MAPREDUCE_RESULT_KEY, new MapReduceResult<>(payloadObject));
            }
         } catch (Exception e) {
            log.error("Failed to put result object into cache", e);
         }
      }

      return result;
   }

   private Task<KOut, VOut, R> configureMapReduceTask() {
      MapReducer.Builder<KOut, VOut, R> builder = mapReducer.builder()
         .source(sourceName)
         .mapper(mapperFqn, mapperParams)
         .reducer(reducerFqn, reducerParams);

      log.info("--------------------");
      String mapReducerName = mapReducer.getClass().getName();
      if (mapReducer.supportsTimeout()) {
         log.info(mapReducerName + " supports MapReducer.setTimeout()");
         builder.timeout(timeout);
      } else {
         log.info(mapReducerName + " does not support MapReducer.setTimeout()");
      }
      if (mapReducer.supportsCombiner()) {
         log.info(mapReducerName + " supports MapReducer.setCombiner()");
         if (combinerFqn != null) {
            builder.combiner(combinerFqn, combinerParams);
         }
      } else {
         log.info(mapReducerName + " does not support MapReducer.setCombiner()");
      }
      if (collatorFqn != null) {
         builder.collator(collatorFqn, collatorParams);
      }

      return builder.build();
   }

   private DistStageAck executeMapReduceTask(Task<KOut, VOut, R> task, Statistics stats) {
      MapReduceAck ack = new MapReduceAck(slaveState);
      try {
         if (collatorFqn != null) {
            Request request = stats.startRequest();
            payloadObject = task.executeWithCollator();
            // TODO handle exception
            request.succeeded(MapReducer.MAPREDUCE_COLLATOR);
            log.info("MapReduce task with Collator completed in "
               + Utils.prettyPrintTime(request.duration(), TimeUnit.NANOSECONDS));
            ack.setStats(stats);
            if (printResult) {
               log.info("MapReduce result: " + payloadObject.toString());
            }
         } else {
            Request request = stats.startRequest();
            payloadMap = task.execute();
            // TODO handle exception
            request.succeeded(MapReducer.MAPREDUCE);

            if (payloadMap == null) {
               if (storeResult) {
                  log.info("MapReduce task completed in " + Utils.prettyPrintTime(request.duration(), TimeUnit.NANOSECONDS));

                  MapReduceResult<KOut, VOut, R> mapReduceResult = (MapReduceResult) slaveState.get(MAPREDUCE_RESULT_KEY);
                  int totalSize = 0;
                  if (mapReduceResult != null) {
                     if (mapReduceResult.payloadMap != null) {
                        totalSize = mapReduceResult.payloadMap.size();
                     } else if (mapReduceResult.payloadObject != null) {
                        totalSize = 1;
                     }
                  }

                  log.info("Result map contains '" + totalSize + "' keys.");
                  ack.setNumberOfResultKeys(totalSize);
                  ack.setStats(stats);
                  if (printResult && mapReduceResult != null) {
                     log.info("MapReduce result:");
                     if (mapReduceResult.payloadMap != null) {
                        for (Map.Entry<KOut, VOut> entry : mapReduceResult.payloadMap.entrySet()) {
                           log.info("key: " + entry.getKey() + " value: " + entry.getValue());
                        }
                     } else if (mapReduceResult.payloadObject != null) {
                        log.info(mapReduceResult.payloadObject.toString());
                     }
                  }
               } else {
                  ack.setStats(stats);
                  ack.error("executeMapReduceTask() returned null");
               }
            } else {
               log.info("MapReduce task completed in " + Utils.prettyPrintTime(request.duration(), TimeUnit.NANOSECONDS));
               log.info("Result map contains '" + payloadMap.keySet().size() + "' keys.");
               ack.setNumberOfResultKeys(payloadMap.keySet().size());
               ack.setStats(stats);

               if (printResult) {
                  log.info("MapReduce result:");
                  for (Map.Entry<KOut, VOut> entry : payloadMap.entrySet()) {
                     log.info("key: " + entry.getKey() + " value: " + entry.getValue());
                  }
               }
            }
         }
      } catch (Exception e) {
         ack.error("executeMapReduceTask() threw an exception", e);
         log.error("executeMapReduceTask() returned an exception", e);
      }
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

   private static class MapReduceResult<KOut, VOut, R> {

      private Map<KOut, VOut> payloadMap = null;
      private R payloadObject = null;

      private MapReduceResult(Map<KOut, VOut> payloadMap, R payloadObject) {
         this.payloadMap = payloadMap;
         this.payloadObject = payloadObject;
      }

      public MapReduceResult(Map<KOut, VOut> payloadMap) {
         this(payloadMap, null);
      }

      public MapReduceResult(R payloadObject) {
         this(null, payloadObject);
      }
   }

}
