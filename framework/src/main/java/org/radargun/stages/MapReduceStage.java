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
package org.radargun.stages;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.MapReduceCapable;
import org.radargun.state.MasterState;
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
   
   @Property(doc = "A tiemout value for the remote communication that happens "
         + "during a Map/Reduce task. The default is zero which means to wait forever.")
   private long timeout = 0;

   @Property(doc = "The java.util.concurrent.TimeUnit to use with the timeout "
         + "property. The default is TimeUnit.MILLISECONDS")
   private TimeUnit unit = TimeUnit.MILLISECONDS;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      super.processAckOnMaster(acks, masterState);
      StringBuilder reportCsvContent = new StringBuilder();

      if (masterState.get(FIRST_SCALE_STAGE_KEY) == null) {
         masterState.put(FIRST_SCALE_STAGE_KEY, masterState.nameOfTheCurrentBenchmark());
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
      DefaultDistStageAck result = newDefaultStageAck();
      CacheWrapper cacheWrapper = slaveState.getCacheWrapper();

      if (cacheWrapper == null) {
         result.setError(true);
         result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
         return result;
      }

      if (!(cacheWrapper instanceof MapReduceCapable)) {
         result.setError(true);
         result.setErrorMessage("MapReduce tasks are not supported by this cache");
         return result;
      }

      if (mapperFqn == null || reducerFqn == null) {
         result.setError(true);
         result.setErrorMessage("Both the mapper and reducer class must be specified.");
         return result;
      }

      if (getSlaveIndex() == 0) {
         @SuppressWarnings("unchecked")
         MapReduceCapable<KOut, VOut, R> mapReduceCapable = (MapReduceCapable<KOut, VOut, R>) cacheWrapper;
         result = executeMapReduceTask(mapReduceCapable);
      } else {
         String payload = this.slaveIndex + ", " + cacheWrapper.getNumMembers() + ", " + cacheWrapper.getLocalSize()
               + ", 0, -1";
         result.setPayload(payload);
      }
      return result;
   }

   private DefaultDistStageAck executeMapReduceTask(MapReduceCapable<KOut, VOut, R> mapReduceCapable) {
      DefaultDistStageAck result = newDefaultStageAck();
      Map<KOut, VOut> payloadMap = null;
      R payloadObject = null;
      long durationNanos;
      long start;

      log.info("--------------------");
      mapReduceCapable.setParameters(Utils.parseParams(mapperParams), Utils.parseParams(reducerParams),
            Utils.parseParams(collatorParams));
      if (mapReduceCapable.setDistributeReducePhase(distributeReducePhase)) {
         log.info(mapReduceCapable.getClass().getName() + " supports MapReduceCapable.setDistributeReducePhase()");
      } else {
         log.info(mapReduceCapable.getClass().getName()
               + " does not support MapReduceCapable.setDistributeReducePhase()");
      }
      if (mapReduceCapable.setUseIntermediateSharedCache(useIntermediateSharedCache)) {
         log.info(mapReduceCapable.getClass().getName()
               + " supports MapReduceCapable.setUseIntermediateSharedCache()");
      } else {
         log.info(mapReduceCapable.getClass().getName()
               + " does not support MapReduceCapable.setUseIntermediateSharedCache()");
      }
      if (mapReduceCapable.setTimeout(timeout, unit)) {
         log.info(mapReduceCapable.getClass().getName()
               + " supports MapReduceCapable.setTimeout()");
      } else {
         log.info(mapReduceCapable.getClass().getName()
               + " does not support MapReduceCapable.setTimeout()");
      }
      try {
         if (collatorFqn != null) {
            start = System.nanoTime();
            payloadObject = mapReduceCapable.executeMapReduceTask(classLoadHelper, mapperFqn, reducerFqn, collatorFqn);
            durationNanos = System.nanoTime() - start;
            log.info("MapReduce task with Collator completed in "
                  + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
            String payload = this.slaveIndex + ", " + mapReduceCapable.getNumMembers() + ", "
                  + mapReduceCapable.getLocalSize() + ", " + durationNanos + ", -1";
            result.setPayload(payload);
            if (storeResultInCache) {
               try {
                  mapReduceCapable.put(null, MAPREDUCE_RESULT_KEY, payloadObject);
               } catch (Exception e) {
                  log.error("Failed to put collated result object into cache", e);
               }
            }
         } else {
            start = System.nanoTime();
            payloadMap = mapReduceCapable.executeMapReduceTask(classLoadHelper, mapperFqn, reducerFqn);
            durationNanos = System.nanoTime() - start;

            if (payloadMap != null) {
               log.info("MapReduce task completed in " + Utils.prettyPrintTime(durationNanos, TimeUnit.NANOSECONDS));
               log.info("Result map contains '" + payloadMap.keySet().size() + "' keys.");
               String payload = this.slaveIndex + ", " + mapReduceCapable.getNumMembers() + ", "
                     + mapReduceCapable.getLocalSize() + ", " + durationNanos + ", " + payloadMap.keySet().size();
               result.setPayload(payload);
               if (storeResultInCache) {
                  try {
                     mapReduceCapable.put(null, MAPREDUCE_RESULT_KEY, payloadMap);
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
      log.info(mapReduceCapable.getNumMembers() + " nodes were used. " + mapReduceCapable.getLocalSize()
            + " entries on this node");
      log.info(mapReduceCapable.getInfo());
      log.info("--------------------");

      return result;
   }
}
