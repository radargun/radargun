package org.radargun.stages;

import java.io.File;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.lifecycle.LifecycleHelper;
import org.radargun.utils.Utils;

/**
 * Distributed stage that will stop the cache wrapper on each slave.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario. You can alter the properties in &lt;cleanup/&gt element.")
public class ScenarioCleanupStage extends AbstractDistStage {

   @Property(doc = "Specifies whether the check for amount of free memory should be performed. Default is true.")
   private boolean checkMemoryReleased = true;

   @Property(doc = "If the free memory after wrapper destroy and System.gc() is below percentage specified in this property the benchmark will stop. Default is 95.")
   private byte memoryThreshold = 95;

   @Property(doc = "Directory where the heap dump will be produced if the memory threshold is hit. By default the dump will not be produced.")
   private String heapDumpDir = null;

   public DistStageAck executeOnSlave() {
      log.info("Scenario finished, running cleanup...");
      try {
         if (lifecycle != null && lifecycle.isRunning()) {
            LifecycleHelper.stop(slaveState, true, false);
            //reset the class loader to SystemClassLoader
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            log.info("Service successfully stopped.");
         } else {
            log.info("No service deployed on this slave, nothing to do.");
            return successfulResponse();
         }
      } catch (Exception e) {
         return errorResponse("Problems shutting down the slave", e);
      } finally {
         log.info(Utils.printMemoryFootprint(true));
         if (checkMemoryReleased) {
            checkMemoryReleased();
         } else {
            System.gc();
         }
         log.info(Utils.printMemoryFootprint(false));
      }
      return successfulResponse();
   }


   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean result = super.processAckOnMaster(acks);
      if (masterState.get(ScenarioInitStage.INITIAL_FREE_MEMORY) == null) {
         masterState.put(ScenarioInitStage.INITIAL_FREE_MEMORY, "");
         for (DistStageAck distStageAck : acks) {
            String key = ScenarioInitStage.INITIAL_FREE_MEMORY + "_" + distStageAck.getSlaveIndex();
            Long freeMemory = (Long) ((DefaultDistStageAck) distStageAck).getPayload();
            log.info(String.format("Node %d has an initial free memory of: %d kb", distStageAck.getSlaveIndex(), freeMemory / 1024));
            masterState.put(key, freeMemory);
         }
      }
      return result;
   }

   @Override
   protected DefaultDistStageAck newDefaultStageAck() {
      DefaultDistStageAck ack = super.newDefaultStageAck();
      ack.setPayload(Runtime.getRuntime().freeMemory());
      return ack;
   }

   private void checkMemoryReleased() {
      long percentage = -1;
      long currentFreeMemory = -1;
      long initialFreeMemory = (Long) slaveState.get(ScenarioInitStage.INITIAL_FREE_MEMORY);
      for (int i = 0; i < 30; i++) {
         System.gc();
         currentFreeMemory = Runtime.getRuntime().freeMemory();
         percentage = (currentFreeMemory * 100) / initialFreeMemory;
         if (percentage >= memoryThreshold) break;
         Utils.sleep(1000);
      }
      log.info(String.format("Free memory: %d kb (%d%% from the initial free memory - %d kb)", currentFreeMemory / 1024, percentage, initialFreeMemory / 1024));
      if (percentage < memoryThreshold) {
         String msg = "Actual percentage of memory smaller than expected!";
         log.error(msg);
         if (heapDumpDir != null) {
            try {
               Utils.dumpHeap(new File(heapDumpDir, slaveState.getConfigName() + ".bin").getAbsolutePath());
            } catch (Exception e) {
               log.error("Cannot produce heap dump!", e);
            }
         }
         throw new IllegalStateException(msg);
      }
   }
}
