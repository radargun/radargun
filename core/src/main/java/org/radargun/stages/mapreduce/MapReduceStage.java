/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages.mapreduce;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
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
         + "final results of the MapReduceTask are stored in the cache at key MAPREDUCE_RESULT_KEY. "
         + "Enabling this feature will require extra DRAM usage. The default is false.")
   private boolean storeResultInCache = false;

   @Property(optional = true, doc = "Boolean value that determines if the "
         + "final results of the MapReduceTask are written to the log of the "
         + "first slave node. The default is false.")
   private boolean printResult = false;

   @Property(doc = "A tiemout value for the remote communication that happens "
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
      super.processAckOnMaster(acks);
      StringBuilder reportCsvContent = new StringBuilder();

      // TODO: move this into test report
      if (masterState.get(FIRST_SCALE_STAGE_KEY) == null) {
         masterState.put(FIRST_SCALE_STAGE_KEY, masterState.getConfigName());
         reportCsvContent
               .append("NODE_INDEX, NUMBER_OF_NODES, KEY_COUNT_ON_NODE, DURATION_NANOSECONDS, KEY_COUNT_IN_RESULT_MAP\n");
      }

      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         reportCsvContent.append((String) dack.getPayload()).append("\n");
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
      DefaultDistStageAck result = newDefaultStageAck();

      if (mapperFqn == null || reducerFqn == null) {
         return errorResponse("Both the mapper and reducer class must be specified.", null);
      }

      if (slaveState.getSlaveIndex() == 0) {
         result = executeMapReduceTask(mapReducer);
      } else {
         result.setPayload(getPayload("0", "-1"));
      }
      return result;
   }

   private String getPayload(String duration, String resultSize) {
      return slaveState.getSlaveIndex() + ", " + clustered.getClusteredNodes() + ", " + cacheInformation.getCache(null).getLocalSize() + ", " + duration + ", " + resultSize;
   }

   private DefaultDistStageAck executeMapReduceTask(MapReducer<KOut, VOut, R> mapReducer) {
      DefaultDistStageAck result = newDefaultStageAck();
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
         log.info(mapReducer.getClass().getName()
               + " does not support MapReducer.setDistributeReducePhase()");
      }
      if (mapReducer.setUseIntermediateSharedCache(useIntermediateSharedCache)) {
         log.info(mapReducer.getClass().getName() + " supports MapReducer.setUseIntermediateSharedCache()");
      } else {
         log.info(mapReducer.getClass().getName()
               + " does not support MapReducer.setUseIntermediateSharedCache()");
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
      try {
         result.setPayload(getPayload("noDuration", "noResultSize"));
         if (collatorFqn != null) {
            start = System.nanoTime();
            payloadObject = mapReducer.executeMapReduceTask(slaveState.getClassLoadHelper(), mapperFqn, reducerFqn, collatorFqn);
            durationNanos = System.nanoTime() - start;
            log.info("MapReduce task with Collator completed in "
                  + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
            result.setPayload(getPayload(String.valueOf(durationNanos), "-1"));
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
            start = System.nanoTime();
            payloadMap = mapReducer.executeMapReduceTask(slaveState.getClassLoadHelper(), mapperFqn, reducerFqn);
            durationNanos = System.nanoTime() - start;

            if (payloadMap != null) {
               log.info("MapReduce task completed in " + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
               log.info("Result map contains '" + payloadMap.keySet().size() + "' keys.");
               result.setPayload(getPayload(String.valueOf(durationNanos), String.valueOf(payloadMap.keySet().size())));
               if (printResult) {
                  log.info("MapReduce result:");
                  for (Map.Entry<KOut, VOut> entry : payloadMap.entrySet()) {
                     log.info("key: " + entry.getKey() + " value: " + entry.getValue());
                  }
               }
               if (storeResultInCache) {
                  try {
                     basicOperations.getCache(null).put(MAPREDUCE_RESULT_KEY, payloadMap);
                  } catch (Exception e) {
                     log.error("Failed to put result map into cache", e);
                  }
               }
            } else {
               result.setError(true);
               result.setErrorMessage("executeMapReduceTask() returned null");
            }
         }
      } catch (Exception e1) {
         result.setError(true);
         result.setErrorMessage("executeMapReduceTask() threw an exception");
         result.setRemoteException(e1);
         log.error("executeMapReduceTask() returned an exception", e1);
      }
      log.info(clustered.getClusteredNodes() + " nodes were used. " + cacheInformation.getCache(null).getLocalSize() + " entries on this node");
      log.info("--------------------");

      return result;
   }
}
