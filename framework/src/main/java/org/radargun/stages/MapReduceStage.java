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

   private final String FIRST_SCALE_STAGE_KEY = "firstScalingStage";

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Mapper implementation to execute.")
   private String mapperFqn;

   @Property(optional = false, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Reducer implementation to execute.")
   private String reducerFqn;

   @Property(optional = true, doc = "Fully qualified class name of the "
         + "org.infinispan.distexec.mapreduce.Collator implementation to execute. The default is null.")
   private String collatorFqn = null;

   @Property(optional = true, doc = "Boolean value that determines if the "
         + "Reduce phase of the MapReduceTask is distributed. The default is true.")
   private boolean distributeReducePhase = true;

   @Property(optional = true, doc = "Boolean value that determines if the "
         + "intermediate results of the MapReduceTask are shared. The default is true.")
   private boolean useIntermediateSharedCache = true;

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      super.processAckOnMaster(acks, masterState);
      StringBuilder reportCsvContent = new StringBuilder();

      if (masterState.get(FIRST_SCALE_STAGE_KEY) == null) {
         masterState.put(FIRST_SCALE_STAGE_KEY, masterState.nameOfTheCurrentBenchmark());
         reportCsvContent
               .append("NODE_INDEX, NUMBER_OF_NODES, KEY_COUNT_ON_NODE, DURATION_MSEC, KEY_COUNT_IN_RESULT_MAP\n");
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
      Map<KOut, VOut> payloadMap = null;
      R payloadObject = null;

      if (cacheWrapper == null) {
         result.setErrorMessage("Not running test on this slave as the wrapper hasn't been configured.");
      } else {
         if (getSlaveIndex() == 0) {
            if (cacheWrapper instanceof MapReduceCapable) {
               if (mapperFqn != null && reducerFqn != null) {
                  log.info("--------------------");
                  @SuppressWarnings("unchecked")
                  MapReduceCapable<KOut, VOut, R> mapReduceCapable = (MapReduceCapable<KOut, VOut, R>) cacheWrapper;
                  if (mapReduceCapable.setDistributeReducePhase(distributeReducePhase)) {
                     log.info(cacheWrapper.getClass().getName()
                           + " supports MapReduceCapable.setDistributeReducePhase()");
                  } else {
                     log.info(cacheWrapper.getClass().getName()
                           + " does not support MapReduceCapable.setDistributeReducePhase()");
                  }
                  if (mapReduceCapable.setUseIntermediateSharedCache(useIntermediateSharedCache)) {
                     log.info(cacheWrapper.getClass().getName()
                           + " supports MapReduceCapable.setUseIntermediateSharedCache()");
                  } else {
                     log.info(cacheWrapper.getClass().getName()
                           + " does not support MapReduceCapable.setUseIntermediateSharedCache()");
                  }
                  long durationMillis;
                  if (collatorFqn != null) {
                     long start = System.currentTimeMillis();
                     payloadObject = mapReduceCapable.executeMapReduceTask(classLoadHelper, mapperFqn, reducerFqn,
                           collatorFqn);
                     durationMillis = System.currentTimeMillis() - start;
                     log.info("MapReduce task with Collator completed in " + Utils.prettyPrintMillis(durationMillis));
                     String payload = this.slaveIndex + ", " + cacheWrapper.getNumMembers() + ", "
                           + cacheWrapper.getLocalSize() + ", " + durationMillis + ", -1";
                     result.setPayload(payload);
                  } else {
                     long start = System.currentTimeMillis();
                     payloadMap = mapReduceCapable.executeMapReduceTask(classLoadHelper, mapperFqn, reducerFqn);
                     durationMillis = System.currentTimeMillis() - start;

                     log.info("MapReduce task completed in " + Utils.prettyPrintMillis(durationMillis));
                     log.info("Result map contains '" + payloadMap.keySet().size() + "' keys.");
                     String payload = this.slaveIndex + ", " + cacheWrapper.getNumMembers() + ", "
                           + cacheWrapper.getLocalSize() + ", " + durationMillis + ", " + payloadMap.keySet().size();
                     result.setPayload(payload);
                  }
                  log.info(cacheWrapper.getNumMembers() + " nodes were used. " + cacheWrapper.getLocalSize()
                        + " entries on this node");
                  log.info(cacheWrapper.getInfo());
                  log.info("--------------------");
               } else {
                  result.setError(true);
                  result.setErrorMessage("Both the mapper and reducer class must be specified.");
               }
            } else {
               result.setError(true);
               result.setErrorMessage("MapReduce tasks are not supported by this cache");
            }
         } else {
            String payload = this.slaveIndex + ", " + cacheWrapper.getNumMembers() + ", " + cacheWrapper.getLocalSize()
                  + ", 0, -1";
            result.setPayload(payload);
         }
      }
      return result;
   }

   public String getMapperFqn() {
      return mapperFqn;
   }

   public void setMapperFqn(String mapperFqn) {
      this.mapperFqn = mapperFqn;
   }

   public String getReducerFqn() {
      return reducerFqn;
   }

   public void setReducerFqn(String reducerFqn) {
      this.reducerFqn = reducerFqn;
   }

   public String getCollatorFqn() {
      return collatorFqn;
   }

   public void setCollatorFqn(String collatorFqn) {
      this.collatorFqn = collatorFqn;
   }
}
