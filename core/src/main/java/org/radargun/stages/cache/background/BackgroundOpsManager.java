package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.helpers.Range;
import org.radargun.state.ServiceListenerAdapter;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.CacheListeners;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Debugable;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.Transactional;

/**
 * Manages background stressors and log checkers (start/stop/check for errors).
 *
 * //TODO: more polishing to make this class agnostic to implemented logic (just pass configuration)
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BackgroundOpsManager extends ServiceListenerAdapter {
   /**
    * Key to SlaveState to retrieve BackgroundOpsManager instance and to MasterState to retrieve results.
    */
   private static final String PREFIX = "BackgroundOps.";
   public static final String DEFAULT = "Default";
   public static final String ALL = PREFIX + "All";

   private static Log log = LogFactory.getLog(BackgroundOpsManager.class);

   private GeneralConfiguration generalConfiguration;
   private LegacyLogicConfiguration legacyLogicConfiguration;
   private LogLogicConfiguration logLogicConfiguration;

   private String name;
   private SlaveState slaveState;
   private volatile Stressor[] stressorThreads;
   private ScheduledFuture keepAliveTask;
   private ScheduledExecutorService executor;
   private boolean loaded = false;
   private LogChecker[] logCheckers;
   private StressorRecordPool stressorRecordPool;
   private boolean stressorsPaused, checkersPaused;

   private Lifecycle lifecycle;
   private CacheListeners listeners;
   private volatile BasicOperations.Cache basicCache;
   private volatile Debugable.Cache debugableCache;
   private volatile Transactional transactional;
   private volatile ConditionalOperations.Cache conditionalCache;
   private volatile CacheInformation.Cache cacheInfo;

   private BackgroundOpsManager() {
   }

   public static BackgroundOpsManager getInstance(SlaveState slaveState, String name) {
      return (BackgroundOpsManager) slaveState.get(PREFIX + name);
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState, String name) {
      BackgroundOpsManager instance = getInstance(slaveState, name);
      if (instance == null) {
         instance = new BackgroundOpsManager();
         instance.name = name;
         instance.slaveState = slaveState;
         instance.executor = Executors.newScheduledThreadPool(1);
         slaveState.put(PREFIX + name, instance);
         slaveState.addServiceListener(instance);
         List<BackgroundOpsManager> list = (List<BackgroundOpsManager>) slaveState.get(ALL);
         if (list == null) {
            slaveState.put(ALL, list = new ArrayList<>());
         }
         list.add(instance);
      }
      return instance;
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState, String name,
                                                          GeneralConfiguration generalConfiguration,
                                                          LegacyLogicConfiguration legacyLogicConfiguration,
                                                          LogLogicConfiguration logLogicConfiguration) {
      BackgroundOpsManager instance = getOrCreateInstance(slaveState, name);
      instance.generalConfiguration = generalConfiguration;
      instance.legacyLogicConfiguration = legacyLogicConfiguration;
      instance.logLogicConfiguration = logLogicConfiguration;

      instance.lifecycle = slaveState.getTrait(Lifecycle.class);
      instance.listeners = slaveState.getTrait(CacheListeners.class);
      instance.loadCaches();
      return instance;
   }

   public static List<BackgroundOpsManager> getAllInstances(SlaveState slaveState) {
      List<BackgroundOpsManager> instances = (List<BackgroundOpsManager>) slaveState.get(ALL);
      return instances != null ? instances : Collections.EMPTY_LIST;
   }

   private void loadCaches() {
      if (lifecycle != null && !lifecycle.isRunning()) {
         log.warn("Can't load caches, service is not running");
         return;
      }
      basicCache = slaveState.getTrait(BasicOperations.class).getCache(generalConfiguration.cacheName);
      ConditionalOperations conditionalOperations = slaveState.getTrait(ConditionalOperations.class);
      conditionalCache = conditionalOperations == null ? null : conditionalOperations.getCache(generalConfiguration.cacheName);
      Debugable debugable = slaveState.getTrait(Debugable.class);
      debugableCache = debugable == null ? null : debugable.getCache(generalConfiguration.cacheName);
      if (generalConfiguration.transactionSize > 0) {
         transactional = slaveState.getTrait(Transactional.class);
         if (transactional == null) {
            throw new IllegalArgumentException("Transactions are set on but the service does not provide transactions");
         } else if (transactional.getConfiguration(generalConfiguration.cacheName) == Transactional.Configuration.NON_TRANSACTIONAL) {
            throw new IllegalArgumentException("Transactions are set on but the cache is not configured as transactional");
         }
      }
      CacheInformation cacheInformation = slaveState.getTrait(CacheInformation.class);
      cacheInfo = cacheInformation == null ? null : cacheInformation.getCache(generalConfiguration.cacheName);
   }

   private void unloadCaches() {
      basicCache = null;
      conditionalCache = null;
      debugableCache = null;
      cacheInfo = null;
   }

   public synchronized void startBackgroundThreads() {
      if (legacyLogicConfiguration.isNoLoading()) {
         setLoaded(true);
      }
      if (legacyLogicConfiguration.loadDataOnSlaves != null
            && !legacyLogicConfiguration.loadDataOnSlaves.isEmpty()
            && !legacyLogicConfiguration.loadDataOnSlaves.contains(slaveState.getSlaveIndex())) {
         log.info("This slave is not loading any data.");
         return;
      }
      if (stressorThreads != null) {
         log.warn("Can't start stressors, they're already running.");
         return;
      }
      if (lifecycle != null && !lifecycle.isRunning()) {
         log.warn("Can't start stressors, service is not running");
         return;
      }
      startStressorThreads();
      if (logLogicConfiguration.enabled) {
         createLogCheckerPool();
         startCheckerThreads();
         if (logLogicConfiguration.ignoreDeadCheckers) {
            keepAliveTask = executor.scheduleAtFixedRate(new KeepAliveTask(), 0, 1000, TimeUnit.MILLISECONDS);
         }
      }
      if (legacyLogicConfiguration.waitUntilLoaded) {
         log.info("Waiting until all stressor threads load data");
         try {
            waitUntilLoaded();
         } catch (InterruptedException e) {
            log.warn("Waiting for loading interrupted", e);
         }
      }
      setLoaded(true);
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
         stressorThreads[i] = new Stressor(this, createLogic(i), i);
         stressorThreads[i].start();
      }
   }

   private Logic createLogic(int index) {
      int numThreads = generalConfiguration.numThreads;
      if (generalConfiguration.sharedKeys) {
         if (logLogicConfiguration.enabled) {
            return new SharedLogLogic(this, new Range(generalConfiguration.keyIdOffset, generalConfiguration.keyIdOffset + generalConfiguration.numEntries));
         } else {
            throw new IllegalArgumentException("Legacy logic cannot use shared keys.");
         }
      } else {
         // TODO: we may have broken totalThreads for legacy logic with dead slaves preloading
         int totalThreads = numThreads * slaveState.getGroupSize();
         Range range = Range.divideRange(generalConfiguration.numEntries, totalThreads, numThreads * slaveState.getIndexInGroup() + index).shift(generalConfiguration.keyIdOffset);

         if (logLogicConfiguration.enabled) {
            int threadId = numThreads * slaveState.getIndexInGroup() + index;
            log.tracef("Stressor %d has range %s", threadId, range);
            return new PrivateLogLogic(this, range);
         } else {
            // TODO: remove preloading from background stressors at all, use load-data instead
            List<Integer> deadSlaves = legacyLogicConfiguration.loadDataForDeadSlaves;
            List<List<Range>> rangesForThreads = null;
            int liveId = slaveState.getSlaveIndex();
            if (!loaded && deadSlaves != null && !deadSlaves.isEmpty()) {
               List<Range> deadSlavesKeyRanges = new ArrayList<Range>(deadSlaves.size() * numThreads);
               for (int deadSlave : deadSlaves) {
                  // key ranges for the current dead slave
                  for (int deadThread = 0; deadThread < numThreads; ++deadThread) {
                     deadSlavesKeyRanges.add(Range.divideRange(generalConfiguration.numEntries, totalThreads, deadSlave * numThreads + deadThread));
                  }
                  if (deadSlave < slaveState.getSlaveIndex()) liveId--;
               }
               rangesForThreads = Range.balance(deadSlavesKeyRanges, (slaveState.getClusterSize() - deadSlaves.size()) * numThreads);
            }
            List<Range> deadRanges = rangesForThreads == null ? null : rangesForThreads.get(index + numThreads * liveId);
            return new LegacyLogic(this, range, deadRanges, loaded);
         }
      }
   }

   private synchronized void startCheckerThreads() {
      if (checkersPaused) {
         log.info("Checkers are paused, not starting");
         return;
      }
      if (logLogicConfiguration.checkingThreads <= 0) {
         log.error("LogValue checker set to 0!");
      } else if (logCheckers != null) {
         throw new IllegalStateException("Log checkers are started");
      } else {
         logCheckers = new LogChecker[logLogicConfiguration.checkingThreads];
         for (int i = 0; i < logLogicConfiguration.checkingThreads; ++i) {
            if (generalConfiguration.sharedKeys) {
               logCheckers[i] = new SharedLogChecker(i, stressorRecordPool, this);
            } else {
               logCheckers[i] = new PrivateLogChecker(i, stressorRecordPool, this);
            }
            logCheckers[i].start();
         }
      }
   }

   private synchronized void createLogCheckerPool() {
      if (stressorRecordPool != null) {
         log.debug("Checker pool already exists, not creating another");
         return;
      }
      int totalThreads = slaveState.getGroupSize() * generalConfiguration.numThreads;
      List<StressorRecord> stressorRecords = new ArrayList<>();
      if (generalConfiguration.sharedKeys) {
         // initialize stressor records
         for (int threadId = 0; threadId < totalThreads; ++threadId) {
            Range range = new Range(generalConfiguration.keyIdOffset, generalConfiguration.keyIdOffset + generalConfiguration.numEntries);
            stressorRecords.add(new StressorRecord(threadId, range));
            log.tracef("Record for threadId %d has range %s", threadId, range);
         }
         stressorRecordPool = new StressorRecordPool(totalThreads, stressorRecords, this);
      } else {
         // initialize stressor records
         for (int threadId = 0; threadId < totalThreads; ++threadId) {
            Range range = Range.divideRange(generalConfiguration.getNumEntries(), totalThreads, threadId).shift(generalConfiguration.keyIdOffset);
            stressorRecords.add(new StressorRecord(threadId, range));
            log.tracef("Record for threadId %d has range %s", threadId, range);
         }
         stressorRecordPool = new StressorRecordPool(totalThreads, stressorRecords, this);
      }
   }

   /**
    *
    * Waits until all stressor threads load data.
    *
    * @throws InterruptedException
    *
    */
   public synchronized void waitUntilLoaded() throws InterruptedException {
      if (logLogicConfiguration.isEnabled()) {
         log.warn("Not waiting as log logic does not preload data.");
         return;
      }
      if (stressorThreads == null) {
         log.info("Not loading, no stressors alive.");
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
               log.warn("Thread " + st.getName() + " has logic " + st.getLogic());
            }
         }
         if (!loaded) {
            Thread.sleep(100);
         }
      }
   }

   public String waitUntilChecked() {
      if (stressorRecordPool == null || logCheckers == null) {
         log.warn("No log checker pool or active checkers.");
         return null;
      }
      Stressor[] stressors = stressorThreads;
      if (stressors != null) {
         stopBackgroundThreads(true, false, false);
      }
      String error = stressorRecordPool.waitUntilChecked(logLogicConfiguration.noProgressTimeout);
      if (error != null) {
         return error;
      }
      stopBackgroundThreads(false, true, false);
      stressorsPaused = true;
      checkersPaused = true;
      return null;
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
      long deadline = System.currentTimeMillis() + logLogicConfiguration.getNoProgressTimeout();
      while (!confirmed.isEmpty()) {
         for (Iterator<Map.Entry<Stressor, Long>> iterator = confirmed.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Stressor, Long> entry = iterator.next();
            AbstractLogLogic logic = (AbstractLogLogic) entry.getKey().getLogic();
            long operationId = logic.getLastConfirmedOperation();
            if (operationId != entry.getValue()) {
               iterator.remove();
            }
         }
         if (System.currentTimeMillis() >= deadline) {
            log.info("No progress within timeout");
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
         log.error("Stressors already started.");
      }
      startCheckerThreads();
   }

   /**
    * 
    * Stops the stressors, call this before stopping CacheWrapper.
    * 
    */
   public synchronized void stopBackgroundThreads() {
      stopBackgroundThreads(true, true, true);
   }

   private synchronized void stopBackgroundThreads(boolean stressors, boolean checkers, boolean keepAlive) {
      // interrupt all threads
      log.debug("Stopping stressors");
      if (stressors && stressorThreads != null) {
         Stressor.requestTerminate();
      }
      if (checkers && logCheckers != null) {
         LogChecker.requestTerminate();
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
      if (checkers && logCheckers != null) {
         for (int i = 0; i < logCheckers.length; ++i) {
            logCheckers[i].interrupt();
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
         if (checkers && logCheckers != null) {
            for (int i = 0; i < logCheckers.length; ++i) {
               logCheckers[i].join();
            }
         }
         log.debug("All threads have joined");
      } catch (InterruptedException e1) {
         log.error("interrupted while waiting for sizeThread and stressorThreads to stop");
      }
      if (stressors) stressorThreads = null;
      if (checkers) logCheckers = null;
   }

   public KeyGenerator getKeyGenerator() {
      KeyGenerator keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
      if (keyGenerator == null) {
         keyGenerator = new StringKeyGenerator();
         slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      }
      return keyGenerator;
   }

   public synchronized String getError() {
      if (logLogicConfiguration.enabled) {
         if (stressorRecordPool != null
               && (stressorRecordPool.getMissingOperations() > 0 || stressorRecordPool.getMissingNotifications() > 0 || stressorRecordPool.getStaleReads() > 0)) {
            return String.format("Background stressors report %d missing operations, %d missing notifications and %d stale reads!",
                  stressorRecordPool.getMissingOperations(), stressorRecordPool.getMissingNotifications(), stressorRecordPool.getStaleReads());
         }
         if (stressorThreads != null) {
            for (Stressor stressor : stressorThreads) {
               log.info("Stressor: threadId=" + stressor.id + ", " + stressor.getLogic().getStatus());
            }
         }
         if (stressorRecordPool != null) {
            boolean progress = true;
            long now = System.currentTimeMillis();
            for (StressorRecord record : stressorRecordPool.getAvailableRecords()) {
               log.info("Record: " + record.getStatus());
               if (now - record.getLastSuccessfulCheckTimestamp() > logLogicConfiguration.noProgressTimeout) {
                  log.error("No progress in this record for " + (now - record.getLastSuccessfulCheckTimestamp()) + " ms");
                  progress = false;
               }
            }
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
               log.info(sb.toString());
               return "No progress in checkers!";
            }
         }
      }
      return null;
   }

   public boolean isSlaveAlive(int slaveId) {
      Object value = null;
      try {
         value = basicCache.get("__keepAlive_" + slaveId);
      } catch (Exception e) {
         log.error("Failed to retrieve the keep alive timestamp", e);
         return true;
      }
      return value != null &&  value instanceof Long && ((Long) value) > System.currentTimeMillis() - generalConfiguration.deadSlaveTimeout;
   }

   public BasicOperations.Cache getBasicCache() {
      return basicCache;
   }

   public Debugable.Cache getDebugableCache() {
      return debugableCache;
   }

   public Transactional.Transaction newTransaction() {
      return transactional.getTransaction();
   }

   public ConditionalOperations.Cache getConditionalCache() {
      return conditionalCache;
   }

   public CacheListeners getListeners() {
      return listeners;
   }

   public String getName() {
      return name;
   }

   public void setLoaded(boolean loaded) {
      this.loaded = loaded;
   }

   public SlaveState getSlaveState() {
      return slaveState;
   }

   public GeneralConfiguration getGeneralConfiguration() {
      return generalConfiguration;
   }

   public LegacyLogicConfiguration getLegacyLogicConfiguration() {
      return legacyLogicConfiguration;
   }

   public LogLogicConfiguration getLogLogicConfiguration() {
      return logLogicConfiguration;
   }

   public CacheInformation.Cache getCacheInfo() {
      return cacheInfo;
   }

   public Lifecycle getLifecycle() {
      return lifecycle;
   }

   public Stressor[] getStressorThreads() {
      return stressorThreads;
   }

   public StressorRecordPool getStressorRecordPool() {
      return stressorRecordPool;
   }

   // TODO:
   // a) clear listener on the BasicOperations trait
   // b) deal with the fact that the clear can be executed
   public static void beforeCacheClear(SlaveState slaveState) {
      List<BackgroundOpsManager> instances = getAllInstances(slaveState);
      for (BackgroundOpsManager instance : instances) {
         instance.setLoaded(false);
      }
   }

   @Override
   public void afterServiceStart() {
      setLoaded(true); // don't load data at this stage
      loadCaches(); // the object returned by a trait may be invalid after restart
      startBackgroundThreads();
   }

   @Override
   public void beforeServiceStop(boolean graceful) {
      stopBackgroundThreads();
      unloadCaches();
   }

   @Override
   public void afterServiceStop(boolean graceful) {}

   private class KeepAliveTask implements Runnable {
      @Override
      public void run() {
         try {
            basicCache.put("__keepAlive_" + slaveState.getIndexInGroup(), System.currentTimeMillis());
         } catch (Exception e) {
            log.error("Failed to place keep alive timestamp", e);
         }
      }
   }

}
