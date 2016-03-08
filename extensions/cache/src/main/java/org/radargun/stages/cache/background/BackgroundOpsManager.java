package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import org.radargun.utils.TimeService;

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
   private boolean loaded = false;
   private StressorRecordPool stressorRecordPool;
   private FailureManager failureManager;
   private ThreadManager threadManager;

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
         instance.failureManager = new FailureManager(instance);
         instance.threadManager = new ThreadManager(instance);
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
      instance.threadManager.initConfiguration();

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

   public Logic createLogic(int index) {
      int numThreads = generalConfiguration.numThreads;
      if (generalConfiguration.sharedKeys) {
         if (logLogicConfiguration.enabled) {
            return new SharedLogLogic(this, new Range(generalConfiguration.keyIdOffset, generalConfiguration.keyIdOffset + generalConfiguration.numEntries));
         } else {
            throw new IllegalArgumentException("Legacy logic cannot use shared keys");
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

   public synchronized void createStressorRecordPool() {
      if (stressorRecordPool != null) {
         log.debug("Checker pool already exists, not creating another.");
         // When service has been stopped for a longer period of time than LogLogicConfiguration.noProgressTimeout, this can lead
         // to no progress timeouts causing test failures. Avoid this by re-setting records' lastSuccessfulCheck timestamps.
         // When checkers truly show no progress, it will be reliably detected by ThreadManager.waitUntilChecked combined with
         // ThreadManager.waitForProgress.
         if (logLogicConfiguration.ignoreDeadCheckers) {
            for (StressorRecord stressorRecord : stressorRecordPool.getAvailableRecords()) {
               stressorRecord.setLastSuccessfulCheckTimestamp(TimeService.currentTimeMillis());
            }
         }
         return;
      }
      int totalThreads = slaveState.getGroupSize() * generalConfiguration.numThreads;
      List<StressorRecord> stressorRecords = new ArrayList<>();
      // Initialize stressor records
      if (generalConfiguration.sharedKeys) {
         for (int threadId = 0; threadId < totalThreads; ++threadId) {
            Range range = new Range(generalConfiguration.keyIdOffset, generalConfiguration.keyIdOffset + generalConfiguration.numEntries);
            stressorRecords.add(new StressorRecord(threadId, range));
            log.tracef("Record for threadId %d has range %s", threadId, range);
         }
         stressorRecordPool = new StressorRecordPool(totalThreads, stressorRecords, this);
      } else {
         for (int threadId = 0; threadId < totalThreads; ++threadId) {
            Range range = Range.divideRange(generalConfiguration.getNumEntries(), totalThreads, threadId).shift(generalConfiguration.keyIdOffset);
            stressorRecords.add(new StressorRecord(threadId, range));
            log.tracef("Record for threadId %d has range %s", threadId, range);
         }
         stressorRecordPool = new StressorRecordPool(totalThreads, stressorRecords, this);
      }
   }

   public synchronized void createFailureManager() {
      if (failureManager != null) {
         log.debug("Failure holder already exists, not creating another.");
         return;
      }
      failureManager = new FailureManager(this);
   }

   public KeyGenerator getKeyGenerator() {
      KeyGenerator keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
      if (keyGenerator == null) {
         keyGenerator = new StringKeyGenerator();
         slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      }
      return keyGenerator;
   }

   // Starts stressor and checker threads. If ignoreDeadCheckers is specified, ThreadManager.KeepAliveTask is scheduled.
   public synchronized void startBackgroundThreads() {
      if (logLogicConfiguration.enabled) {
         createStressorRecordPool();
         createFailureManager();
      }
      threadManager.startBackgroundThreads();
   }

   // Stops stressor and checker threads, including ThreadManager.KeepAliveTask.
   public synchronized void stopBackgroundThreads() {
      threadManager.stopBackgroundThreads();
   }

   // Waits until all stressor threads load data. Applies to LegacyLogic only.
   public synchronized void waitUntilLoaded() throws InterruptedException {
      threadManager.waitUntilLoaded();
   }

   // 1. Stop stressor threads.
   // 2. Wait until checkers check all operations up to last operation written by stressor.
   // 3. Stop checker threads.
   public String waitUntilChecked() {
      return threadManager.waitUntilChecked();
   }

   // Wait until a change in last performed operation of a stressor is detected.
   public boolean waitForProgress() {
      return threadManager.waitForProgress();
   }

   // Start stressor and checker threads.
   public void resumeAfterChecked() {
      threadManager.resumeAfterChecked();
   }

   // Check whether an error was detected in test run and return it. Otherwise return null.
   public synchronized String getError(boolean failuresOnly) {
      return failureManager.getError(failuresOnly);
   }

   public boolean isSlaveAlive(int slaveId) {
      Long keepAliveTimestamp = null;
      try {
         keepAliveTimestamp = (Long) basicCache.get("__keepAlive_" + slaveId);
      } catch (Exception e) {
         log.error("Failed to retrieve the keep alive timestamp", e);
         return true;
      }
      return keepAliveTimestamp != null && keepAliveTimestamp > TimeService.currentTimeMillis() - generalConfiguration.deadSlaveTimeout;
   }

   public Transactional.Transaction newTransaction() {
      return transactional.getTransaction();
   }

   public BasicOperations.Cache getBasicCache() {
      return basicCache;
   }

   public Debugable.Cache getDebugableCache() {
      return debugableCache;
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

   public StressorRecordPool getStressorRecordPool() {
      return stressorRecordPool;
   }

   public FailureManager getFailureManager() {
      return failureManager;
   }

   public ThreadManager getThreadManager() {
      return threadManager;
   }

   // The following methods handle situation when service is started/stopped. In that case we may need to start/stop
   // background threads, as they access the cache directly.

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

}
