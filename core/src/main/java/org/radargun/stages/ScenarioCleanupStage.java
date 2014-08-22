package org.radargun.stages;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.lifecycle.LifecycleHelper;
import org.radargun.state.SlaveState;
import org.radargun.utils.Projections;
import org.radargun.utils.Utils;

/**
 * Distributed stage that will stop the cache wrapper on each slave.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario. You can alter the properties in &lt;cleanup/&gt element.")
public class ScenarioCleanupStage extends AbstractDistStage {

   @Property(doc = "Specifies whether the check for amount of free memory should be performed. Default is true.", deprecatedName = "checkMemoryReleased")
   private boolean checkMemory = true;

   @Property(doc = "If the free memory after wrapper destroy and System.gc() is below percentage specified in this property the benchmark will stop. Default is 95.")
   private byte memoryThreshold = 95;

   @Property(doc = "Directory where the heap dump will be produced if the memory threshold is hit. By default the dump will not be produced.")
   private String heapDumpDir = null;

   @Property(doc = "Specifies whether the check for unfinished threads should be performed. Default is true.")
   private boolean checkThreads = true;

   public DistStageAck executeOnSlave() {
      log.info("Scenario finished, running cleanup...");
      try {
         if (lifecycle != null && lifecycle.isRunning()) {
            LifecycleHelper.stop(slaveState, true, false);
            log.info("Service successfully stopped.");
         } else {
            log.info("No service deployed on this slave, nothing to do.");
         }
      } catch (Exception e) {
         return errorResponse("Problems shutting down the slave", e);
      } finally {
         //reset the class loader to SystemClassLoader
         Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
         slaveState.resetClassLoader();
      }

      log.info(Utils.printMemoryFootprint(true));
      if (checkMemory) {
         checkMemoryReleased();
      } else {
         System.gc();
      }
      log.info(Utils.printMemoryFootprint(false));

      int unfinishedThreads = 0;
      if (checkThreads) {
         unfinishedThreads = checkThreadsFinished();
      }
      return new CleanupAck(slaveState, (Long) slaveState.get(ScenarioInitStage.INITIAL_FREE_MEMORY), Runtime.getRuntime().freeMemory(), unfinishedThreads);
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      if (!super.processAckOnMaster(acks)) return false;
      boolean result = true;
      for (CleanupAck ack : Projections.instancesOf(acks, CleanupAck.class)) {
         log.info(String.format("Node %d has changed free memory from %d MB to %d MB and has %d unfinished threads",
               ack.getSlaveIndex(), ack.initialFreeMemory / 1048576, ack.finalFreeMemory / 1048576, ack.unfinishedThreads));
         result = result && ack.unfinishedThreads == 0;
      }
      return result;
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

   private int checkThreadsFinished() {
      Thread[] activeThreads = new Thread[Thread.activeCount() * 2];
      int activeCount = Thread.enumerate(activeThreads);
      Set<Thread> threads = new HashSet<>(activeCount);
      for (int i = 0; i < activeCount; ++i) threads.add(activeThreads[i]);
      Set<Thread> initialThreads = (Set<Thread>) slaveState.get(ScenarioInitStage.INITIAL_THREADS);
      threads.removeAll(initialThreads);
      for (Thread thread : threads) {
         StringBuilder sb = new StringBuilder();
         sb.append("Unfinished thread ").append(thread.getName()).append(" (id=")
               .append(thread.getId()).append(", status=").append(thread.getState()).append(")");
         for (StackTraceElement ste : thread.getStackTrace()) {
            sb.append("\n\tat ");
            sb.append(ste.toString());
         }
         log.warn(sb.toString());
      }
      return threads.size();
   }


   private static class CleanupAck extends DistStageAck {
      private final long initialFreeMemory, finalFreeMemory;
      private final int unfinishedThreads;

      private CleanupAck(SlaveState slaveState, long initialFreeMemory, long finalFreeMemory, int unfinishedThreads) {
         super(slaveState);
         this.initialFreeMemory = initialFreeMemory;
         this.finalFreeMemory = finalFreeMemory;
         this.unfinishedThreads = unfinishedThreads;
      }
   }
}
