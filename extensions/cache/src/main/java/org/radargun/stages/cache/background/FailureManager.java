package org.radargun.stages.cache.background;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.TimeService;

/**
 * Manager class holding failures occurring during test run.
 *
 * @author Matej Cimbora
 * @author Radim Vansa
 */
public class FailureManager {

   private static final Log log = LogFactory.getLog(FailureManager.class);

   private BackgroundOpsManager manager;

   private AtomicLong missingOperations = new AtomicLong();
   private AtomicLong missingNotifications = new AtomicLong();
   private AtomicLong staleReads = new AtomicLong();
   private AtomicLong failedTransactionAttempts = new AtomicLong();
   private AtomicLong delayedRemovesErrors = new AtomicLong();

   public FailureManager(BackgroundOpsManager manager) {
      this.manager = manager;
   }

   public long getMissingOperations() {
      return missingOperations.get();
   }

   public long getMissingNotifications() {
      return missingNotifications.get();
   }

   public long getStaleReads() {
      return staleReads.get();
   }

   public long getFailedTransactionAttempts() {
      return failedTransactionAttempts.get();
   }

   public long getDelayedRemovesErrors() {
      return delayedRemovesErrors.get();
   }

   public void reportMissingOperation() {
      missingOperations.incrementAndGet();
   }

   public void reportMissingNotification() {
      missingNotifications.incrementAndGet();
   }

   public void reportStaleRead() {
      staleReads.incrementAndGet();
   }

   public void reportFailedTransactionAttempt() {
      failedTransactionAttempts.incrementAndGet();
   }

   public void reportDelayedRemoveError() {
      delayedRemovesErrors.incrementAndGet();
   }

   public synchronized String getError(boolean failuresOnly) {
      if (!manager.getLogLogicConfiguration().isEnabled()) {
         return null;
      }
      if (getMissingOperations() > 0 || getMissingNotifications() > 0 || getStaleReads() > 0
         || getDelayedRemovesErrors() > 0 || getFailedTransactionAttempts() > 0) {
         return String.format("Background stressors report %d missing operations, %d missing notifications, %d stale reads, " +
               "%d failed transaction attempts and %d delayed removes errors",
            getMissingOperations(), getMissingNotifications(), getStaleReads(),
            getFailedTransactionAttempts(), getDelayedRemovesErrors());
      }
      if (failuresOnly) {
         return null;
      }
      if (!manager.getLifecycle().isRunning()) {
         /**
          * As stressorRecordPool survives service restarts, we might get false suspicion of checkers showing no progress when
          * t(service_not_running) > logLogicConfiguration.noProgressTimeout during time the service is stopped.
          */
         log.debug("Service is not running, skipping verification of checker progress");
         return null;
      }
      // Print statuses of stressor threads.
      Stressor[] stressorThreads = manager.getThreadManager().getStressorThreads();
      if (stressorThreads != null) {
         for (Stressor stressor : stressorThreads) {
            log.debugf("Stressor: threadId=%d, status=%s", stressor.id, stressor.getLogic().getStatus());
         }
      }
      // Iterate over all stressor records in stressor record pool and check whether last successful check was performed within timeout.
      if (manager.getStressorRecordPool() != null) {
         boolean progress = true;
         long now = TimeService.currentTimeMillis();
         for (StressorRecord record : manager.getStressorRecordPool().getAvailableRecords()) {
            log.debugf("Record: status=%s.", record.getStatus());
            // Especially with elasticity tests a node can be dead for a long time period. Check for progress may need to be skipped as stressors
            // on this node can't perform any operations.
            if (manager.getLogLogicConfiguration().ignoreDeadCheckers && !manager.isSlaveAlive(record.getThreadId() / manager.getGeneralConfiguration().getNumThreads())) {
               log.tracef("Node where stressor for this record resides is dead, skipping check");
               continue;
            }
            if (now - record.getLastSuccessfulCheckTimestamp() > manager.getLogLogicConfiguration().noProgressTimeout) {
               log.errorf("No progress in this record for %d ms", now - record.getLastSuccessfulCheckTimestamp());
               progress = false;
            }
         }
         // No checking progress detected, print extended information about stressors/other running threads.
         if (!progress) {
            StringBuilder sb = new StringBuilder(1000);
            if (stressorThreads != null) {
               sb.append("Current stressors info:\n");
               for (Stressor stressor : stressorThreads) {
                  sb.append(stressor.getStatus()).append(", stacktrace:\n");
                  for (StackTraceElement ste : stressor.getStackTrace()) {
                     sb.append(ste).append("\n");
                  }
               }
            } else {
               sb.append("No stressors are running, ");
            }
            sb.append("Other threads:\n");
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
               Thread thread = entry.getKey();
               if (thread.getName().startsWith("StressorThread")) continue;
               sb.append(thread.getName()).append(" (").append(thread.getState()).append("):\n");
               for (StackTraceElement ste : thread.getStackTrace()) {
                  sb.append(ste).append("\n");
               }
            }
            log.error(sb.toString());
            return "No progress in checkers!";
         }
      }
      return null;
   }
}
