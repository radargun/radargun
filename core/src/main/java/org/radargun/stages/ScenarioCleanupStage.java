package org.radargun.stages;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.SlaveState;
import org.radargun.utils.Projections;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.Utils;

/**
 * Distributed stage that will stop the cache wrapper on each slave.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario. You can alter the properties in &lt;cleanup/&gt element.")
public final class ScenarioCleanupStage extends InternalDistStage {

   @Property(doc = "Specifies whether the check for amount of free memory should be performed. Default is true.", deprecatedName = "checkMemoryReleased")
   private boolean checkMemory = true;

   @Property(doc = "If the available (free) memory after service destroy and System.gc() is below percentage specified in this property the benchmark will fail. Default is 95.")
   private int memoryThreshold = 95;

   @Property(doc = "Timeout for releasing memory through garbage collections. Default is 30 seconds.", converter = TimeConverter.class)
   private long memoryReleaseTimeout = 30000;

   @Property(doc = "Directory where the heap dump will be produced if the memory threshold is hit " +
         "or some threads have not finished. By default the dump will not be produced.")
   private String heapDumpDir = null;

   @Property(doc = "Specifies whether the check for unfinished threads should be performed. Default is true.")
   private boolean checkThreads = true;

   @Property(doc = "Calls Thread.stop() on threads that have not finished. Works only if checkThreads=true. Default is true.")
   private boolean stopUnfinishedThreads = true;

   @Property(doc = "Timeout for stopped threads to join. Default is 10 seconds.", converter = TimeConverter.class)
   private long stopTimeout = 10000;

   public DistStageAck executeOnSlave() {
      try {
         int unfinishedThreads = 0;
         if (checkThreads) {
            Set<Thread> unfinished = getUnfinishedThreads();
            unfinishedThreads = unfinished.size();
            if (unfinishedThreads > 0) {
               logUnfinished(unfinished);
               if (stopUnfinishedThreads) {
                  stopUnfinished(unfinished);
               }
            }
         }

         boolean memoryCheckResult = true;
         if (checkMemory) {
            memoryCheckResult = checkMemoryReleased();
         } else {
            System.gc();
         }

         if ((unfinishedThreads > 0 || !memoryCheckResult) && heapDumpDir != null) {
            try {
               File heapDumpFile = new File(heapDumpDir, slaveState.getConfigName() + "." + slaveState.getSlaveIndex()
                     + "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".bin");
               log.info("Dumping heap into " + heapDumpFile.getAbsolutePath());
               Utils.dumpHeap(heapDumpFile.getAbsolutePath());
               log.info("Successfully written heap dump.");
            } catch (Exception e) {
               log.error("Cannot write heap dump!", e);
            }
         }

         Runtime runtime = Runtime.getRuntime();
         long availableMemory = runtime.freeMemory() + runtime.maxMemory() - runtime.totalMemory();
         return new CleanupAck(slaveState, memoryCheckResult, (Long) slaveState.getPersistent(ScenarioInitStage.INITIAL_FREE_MEMORY), availableMemory, unfinishedThreads);
      } finally {
         log.info("Memory after cleanup: \n" + Utils.getMemoryInfo());
      }
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      boolean result = true;
      for (CleanupAck ack : Projections.instancesOf(acks, CleanupAck.class)) {
         log.infof("Node %d has changed available memory from %d MB to %d MB and has %d unfinished threads",
               ack.getSlaveIndex(), ack.initialAvailableMemory / 1048576, ack.finalAvailableMemory / 1048576, ack.unfinishedThreads);
         if (ack.isError()) {
            log.warn("Ack contains errors: " + ack);
         }
         result = result && !ack.isError() && ack.memoryCheckResult && ack.unfinishedThreads == 0;
      }
      return result ? StageResult.SUCCESS : errorResult();
   }

   private boolean checkMemoryReleased() {
      long percentage = -1;
      long currentFreeMemory = -1;
      long initialFreeMemory = (Long) slaveState.getPersistent(ScenarioInitStage.INITIAL_FREE_MEMORY);
      long deadline = System.currentTimeMillis() + memoryReleaseTimeout;
      for (;;) {
         System.gc();
         Runtime runtime = Runtime.getRuntime();
         currentFreeMemory = runtime.freeMemory() + runtime.maxMemory() - runtime.totalMemory();
         percentage = (currentFreeMemory * 100) / initialFreeMemory;
         if (percentage > memoryThreshold || System.currentTimeMillis() > deadline) break;
         log.infof("Available memory: %d kB (%d%% of initial available memory - %d kB)", currentFreeMemory / 1024, percentage, initialFreeMemory / 1024);
         Utils.sleep(1000);
      }
      if (percentage > memoryThreshold) {
         return true;
      }
      log.error("Using more memory than expected!");
      return false;
   }

   private String getThreadId(Thread thread) {
      return String.format("%s (id=%d, state=%s)", thread.getName(), thread.getId(), thread.getState());
   }

   private void logUnfinished(Collection<Thread> threads) {
      for (Thread thread : threads) {
         StringBuilder sb = new StringBuilder();
         sb.append("Unfinished thread " + getThreadId(thread));
         for (StackTraceElement ste : thread.getStackTrace()) {
            sb.append("\n\tat ");
            sb.append(ste.toString());
         }
         log.warn(sb.toString());
      }
   }

   private void stopUnfinished(Collection<Thread> threads) {
      for (Thread thread : threads) {
         log.info("Interrupting thread " + getThreadId(thread));
         thread.interrupt();
      }
      long deadline = System.currentTimeMillis() + stopTimeout/2;
      for (Thread thread : threads) {
         long timeout = deadline - System.currentTimeMillis();
         if (timeout > 0) {
            try {
               thread.join(timeout);
            } catch (InterruptedException e) {
               log.warn("Interrupted when waiting for thread " + getThreadId(thread), e);
            }
         }
      }

      deadline += stopTimeout/2;
      for (Thread thread : threads) {
         if (!thread.isAlive()) continue;
         log.info("Stopping thread " + getThreadId(thread));
         // we can't break anything when doing the cleanup
         thread.stop();
      }
      for (Thread thread : threads) {
         // we can't break anything when doing the cleanup
         long timeout = deadline - System.currentTimeMillis();
         if (timeout > 0) {
            try {
               thread.join(timeout);
            } catch (InterruptedException e) {
               log.warn("Interrupted when waiting for thread " + getThreadId(thread), e);
            }
         }
         log.info("Is thread " + getThreadId(thread) + ") alive? " + thread.isAlive());
      }
   }

   private Set<Thread> getUnfinishedThreads() {
      Thread[] activeThreads = new Thread[Thread.activeCount() * 2];
      int activeCount = Thread.enumerate(activeThreads);
      Set<Thread> threads = new HashSet<>(activeCount);
      for (int i = 0; i < activeCount; ++i) threads.add(activeThreads[i]);
      Set<Thread> initialThreads = (Set<Thread>) slaveState.getPersistent(ScenarioInitStage.INITIAL_THREADS);
      if (initialThreads == null) {
         log.warn("No initial threads!");
         return Collections.EMPTY_SET;
      } else {
         threads.removeAll(initialThreads);
         return threads;
      }
   }


   private static class CleanupAck extends DistStageAck {
      private final boolean memoryCheckResult;
      private final long initialAvailableMemory, finalAvailableMemory;
      private final int unfinishedThreads;

      private CleanupAck(SlaveState slaveState, boolean memoryCheckResult, long initialAvailableMemory, long finalAvailableMemory, int unfinishedThreads) {
         super(slaveState);
         this.memoryCheckResult = memoryCheckResult;
         this.initialAvailableMemory = initialAvailableMemory;
         this.finalAvailableMemory = finalAvailableMemory;
         this.unfinishedThreads = unfinishedThreads;
      }
   }
}
