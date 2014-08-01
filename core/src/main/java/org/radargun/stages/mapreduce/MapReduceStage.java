package org.radargun.stages.mapreduce;

import static org.radargun.utils.Utils.cast;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.CacheInformation.Cache;
import org.radargun.traits.Clustered;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.MapReducer;
import org.radargun.utils.Utils;

/**
 * Executes a MapReduce Task against the cache.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stage which executes a MapReduce Task against all keys in the cache.")
public class MapReduceStage<KOut, VOut, R> extends AbstractDistStage {

   public static final String MAPREDUCE_RESULT_KEY = "mapreduceResult";

   private final String FIRST_SCALE_STAGE_KEY = "firstScalingStage";

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Mapper implementation to execute.")
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
         + "property. The default is TimeUnit.MILLISECONDS")
   private TimeUnit unit = TimeUnit.MILLISECONDS;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private MapReducer mapReducer;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private Clustered clustered;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private CacheInformation cacheInformation;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      if (!super.processAckOnMaster(acks))
         return false;
      StringBuilder reportCsvContent = new StringBuilder();

      // TODO: move this into test report
      if (masterState.get(FIRST_SCALE_STAGE_KEY) == null) {
         masterState.put(FIRST_SCALE_STAGE_KEY, masterState.getConfigName());
         reportCsvContent.append("NODE_INDEX, NUMBER_OF_NODES, KEY_COUNT_ON_NODE, "
               + "DURATION_NANOSECONDS, KEY_COUNT_IN_RESULT_MAP, CONFIGURATION_NAME\n");
      }

      for (TextAck ack : cast(acks, TextAck.class)) {
         reportCsvContent.append(ack.getText()).append("\n");
      }
      reportCsvContent.append("\n");

      try {
         Utils.createOutputFile("mapreduce.csv", reportCsvContent.toString(), false);
      } catch (IOException e) {
         log.error("Failed to create report.", e);
      }

      return true;
   }

   @Override
   public DistStageAck executeOnSlave() {
      if (!isServiceRunnning()) {
         return errorResponse("Service is not runnning", null);
      }

      if (mapperFqn == null || reducerFqn == null) {
         return errorResponse("Both the mapper and reducer class must be specified.", null);
      }

      if (slaveState.getSlaveIndex() == 0) {
         return executeMapReduceTask(mapReducer);
      } else {
         return new TextAck(slaveState).setText(getPayload("0", "-1"));
      }
   }

   private String getPayload(String duration, String resultSize) {
      return slaveState.getSlaveIndex() + ", " + clustered.getClusteredNodes() + ", "
            + cacheInformation.getCache(cacheInformation.getDefaultCacheName()).getLocalSize() + ", " + duration + ", "
            + resultSize + ", " + slaveState.getConfigName();
   }

   private DistStageAck executeMapReduceTask(MapReducer<KOut, VOut, R> mapReducer) {
      Map<KOut, VOut> payloadMap = null;
      R payloadObject = null;
      long durationNanos;
      long start;

      log.info("--------------------");
      mapReducer.setParameters(Utils.parseParams(mapperParams), Utils.parseParams(reducerParams),
            Utils.parseParams(combinerParams), Utils.parseParams(collatorParams));
      if (mapReducer.setDistributeReducePhase(distributeReducePhase)) {
         log.info(mapReducer.getClass().getName() + " supports MapReducer.setDistributeReducePhase()");
      } else {
         log.info(mapReducer.getClass().getName() + " does not support MapReducer.setDistributeReducePhase()");
      }
      if (mapReducer.setUseIntermediateSharedCache(useIntermediateSharedCache)) {
         log.info(mapReducer.getClass().getName() + " supports MapReducer.setUseIntermediateSharedCache()");
      } else {
         log.info(mapReducer.getClass().getName() + " does not support MapReducer.setUseIntermediateSharedCache()");
      }
      if (mapReducer.setTimeout(timeout, unit)) {
         log.info(mapReducer.getClass().getName() + " supports MapReducer.setTimeout()");
      } else {
         log.info(mapReducer.getClass().getName() + " does not support MapReducer.setTimeout()");
      }
      if (mapReducer.setCombiner(combinerFqn)) {
         log.info(mapReducer.getClass().getName() + " supports MapReducer.setCombiner()");
      } else {
         log.info(mapReducer.getClass().getName() + " does not support MapReducer.setCombiner()");
      }
      TextAck ack = new TextAck(slaveState);
      try {
         ack.setText(getPayload("noDuration", "noResultSize"));
         if (collatorFqn != null) {
            start = System.nanoTime();
            payloadObject = mapReducer.executeMapReduceTask(slaveState.getClassLoadHelper(), mapperFqn, reducerFqn,
                  collatorFqn);
            durationNanos = System.nanoTime() - start;
            log.info("MapReduce task with Collator completed in "
                  + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
            ack.setText(getPayload(String.valueOf(durationNanos), "-1"));
            if (printResult) {
               log.info("MapReduce result: " + payloadObject.toString());
            }
            if (storeResultInCache) {
               try {
                  basicOperations.getCache(null).put(MAPREDUCE_RESULT_KEY, payloadObject);
               } catch (Exception e) {
                  log.error("Failed to put collated result object into cache", e);
               }
            }
         } else {
            if (storeResultInCache) {
               if (mapReducer.setResultCacheName(MAPREDUCE_RESULT_KEY)) {
                  log.info(mapReducer.getClass().getName() + " supports MapReducer.setResultCacheName()");
               } else {
                  log.info(mapReducer.getClass().getName() + " does not support MapReducer.setResultCacheName()");
               }
            }
            start = System.nanoTime();
            payloadMap = mapReducer.executeMapReduceTask(slaveState.getClassLoadHelper(), mapperFqn, reducerFqn);
            durationNanos = System.nanoTime() - start;

            if (payloadMap != null) {
               log.info("MapReduce task completed in " + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
               log.info("Result map contains '" + payloadMap.keySet().size() + "' keys.");
               ack.setText(getPayload(String.valueOf(durationNanos), String.valueOf(payloadMap.keySet().size())));
               if (printResult) {
                  log.info("MapReduce result:");
                  for (Map.Entry<KOut, VOut> entry : payloadMap.entrySet()) {
                     log.info("key: " + entry.getKey() + " value: " + entry.getValue());
                  }
               }
            } else {
               if (storeResultInCache) {
                  log.info("MapReduce task completed in " + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
                  log.info("Result map contains '" + cacheInformation.getCache(MAPREDUCE_RESULT_KEY).getTotalSize()
                        + "' keys.");
                  ack.setText(getPayload(String.valueOf(durationNanos),
                        String.valueOf(cacheInformation.getCache(MAPREDUCE_RESULT_KEY).getTotalSize())));
               } else {
                  ack.setText(getPayload("N/A", "N/A"));
                  ack.error("executeMapReduceTask() returned null");
               }
            }

         }
      } catch (Exception e) {
         ack.error("executeMapReduceTask() threw an exception", e);
         log.error("executeMapReduceTask() returned an exception", e);
      }
      log.info(clustered.getClusteredNodes() + " nodes were used. "
            + cacheInformation.getCache(cacheInformation.getDefaultCacheName()).getLocalSize()
            + " entries on this node");
      log.info("--------------------");

      return ack;
   }

   private static class TextAck extends DistStageAck {
      String text;

      private TextAck(SlaveState slaveState) {
         super(slaveState);
      }

      public String getText() {
         return text;
      }

      public TextAck setText(String text) {
         this.text = text;
         return this;
      }
   }
}
