package org.radargun.stages.cache.background;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.TimeService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Component responsible for starting/stopping background stressors and checkers.
 *
 * @author Matej Cimbora
 * @author Radim Vansa
 */
public class ThreadManager {

   private static final Log log = LogFactory.getLog(ThreadManager.class);

   private BackgroundOpsManager manager;

   // Stressors and checkers
   private volatile Stressor[] stressorThreads;
   private LogChecker[] checkerThreads;
   private boolean stressorsPaused;
   private boolean checkersPaused;

   // Keep alive runner
   private ScheduledExecutorService keepAliveExecutor = Executors.newScheduledThreadPool(1);
   private ScheduledFuture keepAliveTask;

   // Configuration fields
   private GeneralConfiguration generalConfiguration;
   private LegacyLogicConfiguration legacyLogicConfiguration;
   private LogLogicConfiguration logLogicConfiguration;


   public ThreadManager(BackgroundOpsManager manager) {
      this.manager = manager;
   }

   public void initConfiguration() {
      this.generalConfiguration = manager.getGeneralConfiguration();
      this.legacyLogicConfiguration = manager.getLegacyLogicConfiguration();
      this.logLogicConfiguration = manager.getLogLogicConfiguration();
   }

   public synchronized void startBackgroundThreads() {
      if (legacyLogicConfiguration.isNoLoading()) {
         manager.setLoaded(true);
      }
      if (legacyLogicConfiguration.loadDataOnSlaves != null
            && !legacyLogicConfiguration.loadDataOnSlaves.isEmpty()
            && !legacyLogicConfiguration.loadDataOnSlaves.contains(manager.getSlaveState().getSlaveIndex())) {
         log.info("This slave is not loading any data");
         return;
      }
      if (stressorThreads != null) {
         log.warn("Can't start stressors, they're already running");
         return;
      }
      if (manager.getLifecycle() != null && !manager.getLifecycle().isRunning()) {
         log.warn("Can't start stressors, service is not running");
         return;
      }
      startStressorThreads();
      if (logLogicConfiguration.enabled) {
         startCheckerThreads();
         if (logLogicConfiguration.ignoreDeadCheckers) {
            keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(new KeepAliveTask(), 0, 1000, TimeUnit.MILLISECONDS);
         }
      }
      if (legacyLogicConfiguration.waitUntilLoaded) {
         log.info("Waiting until all stressor threads load data");
         try {
            waitUntilLoaded();
         } catch (InterruptedException e) {
            log.error("Waiting for loading interrupted", e);
         }
      }
      manager.setLoaded(true);
   }

   private synchronized void startStressorThreads() {
      if (stressorsPaused) {
         log.info("Not starting stressors, paused");
         return;
      }
      stressorThreads = new Stressor[generalConfiguration.numThreads];
      if (generalConfiguration.numThreads <= 0) {
         log.warn("Stressor thread number set to 0!");
         return;
      }
      for (int i = 0; i < stressorThreads.length; i++) {
         stressorThreads[i] = new Stressor(manager, manager.createLogic(i), i);
         stressorThreads[i].start();
      }
   }

   private synchronized void startCheckerThreads() {
      if (checkersPaused) {
         log.info("Checkers are paused, not starting");
         return;
      }
      if (logLogicConfiguration.checkingThreads <= 0) {
         log.error("LogValue checker set to 0!");
      } else if (checkerThreads != null) {
         throw new IllegalStateException("Log checkers are started");
      } else {
         checkerThreads = new LogChecker[logLogicConfiguration.checkingThreads];
         for (int i = 0; i < logLogicConfiguration.checkingThreads; ++i) {
            if (generalConfiguration.sharedKeys) {
               checkerThreads[i] = new SharedLogChecker(i, manager);
            } else {
               checkerThreads[i] = new PrivateLogChecker(i, manager);
            }
            checkerThreads[i].start();
         }
      }
   }

   /**
    * Stops the stressors, call this before stopping CacheWrapper.
    */
   public synchronized void stopBackgroundThreads() {
      stopBackgroundThreads(true, true, true);
   }

   private synchronized void stopBackgroundThreads(boolean stressors, boolean checkers, boolean keepAlive) {
      // interrupt all threads
      log.debug("Stopping stressors");
      if (stressors && stressorThreads != null) {
         for (int i = 0; i < stressorThreads.length; i++) {
            stressorThreads[i].requestTerminate();
         }
      }
      if (checkers && checkerThreads != null) {
         for (int i = 0; i < checkerThreads.length; ++i) {
            checkerThreads[i].requestTerminate();
         }
      }
      if (keepAlive && keepAliveTask != null) {
         keepAliveTask.cancel(true);
         keepAliveTask = null;
      }
      // give the threads a second to terminate
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {
         log.error("Thread has been interrupted", e);
         Thread.currentThread().interrupt();
      }
      log.debug("Interrupting stressors");
      if (stressors && stressorThreads != null) {
         for (int i = 0; i < stressorThreads.length; i++) {
            stressorThreads[i].interrupt();
         }
      }
      if (checkers && checkerThreads != null) {
         for (int i = 0; i < checkerThreads.length; ++i) {
            checkerThreads[i].interrupt();
         }
      }

      log.debug("Waiting until all threads join");
      // then wait for them to finish
      try {
         if (stressors && stressorThreads != null) {
            for (int i = 0; i < stressorThreads.length; i++) {
               stressorThreads[i].join();
            }
         }
         if (checkers && checkerThreads != null) {
            for (int i = 0; i < checkerThreads.length; ++i) {
               checkerThreads[i].join();
            }
         }
         log.debug("All threads have joined");
      } catch (InterruptedException e1) {
         log.error("interrupted while waiting for sizeThread and stressorThreads to stop");
      }
      if (stressors) stressorThreads = null;
      if (checkers) checkerThreads = null;
   }

   public synchronized void waitUntilLoaded() throws InterruptedException {
      if (logLogicConfiguration.isEnabled()) {
         log.warn("Not waiting as log logic does not preload data");
         return;
      }
      if (stressorThreads == null) {
         log.info("Not loading, no stressors alive");
         return;
      }
      boolean loaded = false;
      while (!loaded) {
         loaded = true;
         for (Stressor st : stressorThreads) {
            if ((st.getLogic() instanceof LegacyLogic)) {
               boolean isLoaded = ((LegacyLogic) st.getLogic()).isLoaded();
               loaded = loaded && isLoaded;
            } else {
               log.warnf("Thread %s has logic %s", st.getName(), st.getLogic());
            }
         }
         if (!loaded) {
            Thread.sleep(100);
         }
      }
   }

   public String waitUntilChecked() {
      if (manager.getStressorRecordPool() == null || checkerThreads == null) {
         log.warn("No log checker pool or active checkers");
         return null;
      }
      Stressor[] stressors = stressorThreads;
      if (stressors != null) {
         stopBackgroundThreads(true, false, false);
      }
      String error = waitUntilChecked(logLogicConfiguration.noProgressTimeout);
      if (error != null) {
         return error;
      }
      stopBackgroundThreads(false, true, false);
      stressorsPaused = true;
      checkersPaused = true;
      return null;
   }

   public String waitUntilChecked(long timeout) {
      AtomicReferenceArray<StressorRecord> allRecords = manager.getStressorRecordPool().getAllRecords();
      int totalThreads = manager.getStressorRecordPool().getTotalThreads();
      for (int i = 0; i < totalThreads; ++i) {
         StressorRecord record = allRecords.get(i);
         if (record == null) continue;
         try {
            // as the pool survives service restarts, we have to always grab actual cache
            LogChecker.LastOperation lastOperation = (LogChecker.LastOperation) manager.getBasicCache().get(LogChecker.lastOperationKey(record.getThreadId()));
            if (lastOperation == null) {
               log.tracef("Thread %d has no recorded operation", record.getThreadId());
            } else {
               record.addConfirmation(lastOperation.getOperationId(), lastOperation.getTimestamp());
            }
         } catch (Exception e) {
            log.errorf(e, "Failed to read last operation key for thread %d", record.getThreadId(), e);
         }
      }
      for (; ; ) {
         boolean allChecked = true;
         long now = TimeService.currentTimeMillis();
         for (int i = 0; i < totalThreads; ++i) {
            StressorRecord record = allRecords.get(i);
            if (record == null) continue;
            long confirmationTimestamp = record.getCurrentConfirmationTimestamp();
            if (confirmationTimestamp > 0) {
               if (log.isTraceEnabled()) {
                  log.trace(record.getStatus());
               }
               allChecked = false;
               if (record.getLastSuccessfulCheckTimestamp() + timeout < now) {
                  String error = String.format("Waiting for checker for record %s timed out after %d ms", record.getStatus(), now - record.getLastSuccessfulCheckTimestamp());
                  log.error(error);
                  return error;
               }
               break;
            }
         }
         if (allChecked) {
            StringBuilder sb = new StringBuilder("All checks OK: ");
            for (int i = 0; i < totalThreads; ++i) {
               StressorRecord record = allRecords.get(i);
               if (record == null) continue;
               sb.append(record.getThreadId()).append("# ")
                     .append(record.getOperationId()).append(" (")
                     .append(record.getLastConfirmedOperationId()).append("), ");
            }
            log.debug(sb.toString());
            return null;
         }
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted waiting for checkers", e);
            return e.toString();
         }
      }
   }

   public boolean waitForProgress() {
      Stressor[] stressors = stressorThreads;
      if (stressors == null) {
         log.error("Stressors are not running!");
         return false;
      }
      Map<Stressor, Long> confirmed = new HashMap<>(stressors.length);
      for (Stressor stressor : stressors) {
         Logic logic = stressor.getLogic();
         if (logic instanceof AbstractLogLogic) {
            long operationId = ((AbstractLogLogic) logic).getLastConfirmedOperation();
            confirmed.put(stressor, operationId);
         } else {
            log.warnf("Cannot wait for stressor %d as it does not implement LogLogic", stressor.id);
         }
      }
      long deadline = TimeService.currentTimeMillis() + logLogicConfiguration.getNoProgressTimeout();
      while (!confirmed.isEmpty()) {
         for (Iterator<Map.Entry<Stressor, Long>> iterator = confirmed.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Stressor, Long> entry = iterator.next();
            AbstractLogLogic logic = (AbstractLogLogic) entry.getKey().getLogic();
            long operationId = logic.getLastConfirmedOperation();
            if (operationId != entry.getValue()) {
               log.tracef("Operation change detected %d -> %d for stressor %s", operationId, entry.getValue(), entry.getKey());
               iterator.remove();
            }
         }
         if (TimeService.currentTimeMillis() >= deadline) {
            log.error("No progress in stressors within timeout");
            return false;
         }
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
            log.error("Interrupted when waiting for progress", e);
            Thread.currentThread().interrupt();
            return false;
         }
      }
      return true;
   }

   public void resumeAfterChecked() {
      stressorsPaused = false;
      checkersPaused = false;
      if (stressorThreads == null) {
         startStressorThreads();
      } else {
         log.error("Stressors already started");
      }
      startCheckerThreads();
   }

   public Stressor[] getStressorThreads() {
      return stressorThreads;
   }

   private class KeepAliveTask implements Runnable {
      @Override
      public void run() {
         try {
            manager.getBasicCache().put("__keepAlive_" + manager.getSlaveState().getIndexInGroup(), TimeService.currentTimeMillis());
         } catch (Exception e) {
            log.error("Failed to place keep alive timestamp", e);
         }
      }
   }
}
