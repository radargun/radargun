package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Debugable;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.Transactional;

/**
 * 
 * Implements background statistics collectors and stressors. BackgroundOpsManager don't stress the cache
 * to the fullest, they just apply mild continuous load (with wait between requests) to have some
 * statistics about cache throughput during resilience/elasticity tests.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BackgroundOpsManager {
   /**
    * Key to SlaveState to retrieve BackgroundOpsManager instance and to MasterState to retrieve results.
    */
   public static final String NAME = "BackgroundOpsManager";
   private static final String CACHE_SIZE = "Cache size";

   private static Log log = LogFactory.getLog(BackgroundOpsManager.class);

   private StartBackgroundStressorsStage stressorConfiguration;
   private int operations;

   private SlaveState slaveState;
   private volatile Stressor[] stressorThreads;
   private SizeThread sizeThread;
   private KeepAliveThread keepAliveThread;
   private List<List<Statistics>> stats;
   private BackgroundStatsThread backgroundStatsThread;
   private long statsIteration;
   private boolean loaded = false;
   private LogChecker[] logCheckers;
   private LogChecker.Pool logCheckerPool;

   private Lifecycle lifecycle;
   private BasicOperations.Cache basicCache;
   private Debugable.Cache debugableCache;
   private Transactional.Resource transactionalCache;
   private ConditionalOperations.Cache conditionalCache;
   private CacheInformation.Cache cacheInfo;

   public static BackgroundOpsManager getInstance(SlaveState slaveState) {
      return (BackgroundOpsManager) slaveState.get(NAME);
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState) {
      BackgroundOpsManager instance = getInstance(slaveState);
      if (instance == null) {
         instance = new BackgroundOpsManager();
         instance.slaveState = slaveState;
         slaveState.put(NAME, instance);
      }
      return instance;
   }

   public void destroy() {
      slaveState.remove(NAME);
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState, StartBackgroundStressorsStage stage) {
      BackgroundOpsManager instance = getOrCreateInstance(slaveState);
      instance.stressorConfiguration = stage;
      instance.operations = stage.puts + stage.gets + stage.removes;

      instance.lifecycle = slaveState.getTrait(Lifecycle.class);
      instance.loadCaches();
      return instance;
   }

   private void loadCaches() {
      basicCache = slaveState.getTrait(BasicOperations.class).getCache(stressorConfiguration.cacheName);
      ConditionalOperations conditionalOperations = slaveState.getTrait(ConditionalOperations.class);
      conditionalCache = conditionalOperations == null ? null : conditionalOperations.getCache(stressorConfiguration.cacheName);
      Debugable debugable = slaveState.getTrait(Debugable.class);
      debugableCache = debugable == null ? null : debugable.getCache(stressorConfiguration.cacheName);
      if (stressorConfiguration.transactionSize > 0) {
         Transactional transactional = slaveState.getTrait(Transactional.class);
         if (transactional == null || transactional.isTransactional(stressorConfiguration.cacheName)) {
            throw new IllegalArgumentException("Transactions are set on but the cache is not configured as transactional");
         }
         transactionalCache = transactional.getResource(stressorConfiguration.cacheName);
      }
      CacheInformation cacheInformation = slaveState.getTrait(CacheInformation.class);
      cacheInfo = cacheInformation == null ? null : cacheInformation.getCache(stressorConfiguration.cacheName);
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState, long statsIteration) {
      BackgroundOpsManager instance = getOrCreateInstance(slaveState);
      instance.statsIteration = statsIteration;
      return instance;
   }

   private BackgroundOpsManager() {
   }

   public Operation getOperation(Random rand) {
      int r = rand.nextInt(operations);
      if (r < stressorConfiguration.gets) {
         return BasicOperations.GET;
      } else if (r < stressorConfiguration.gets + stressorConfiguration.puts) {
         return BasicOperations.PUT;
      } else return BasicOperations.REMOVE;
   }

   public synchronized void startBackgroundThreads() {
      if (stressorConfiguration.loadDataOnSlaves != null
            && !stressorConfiguration.loadDataOnSlaves.isEmpty()
            && !stressorConfiguration.loadDataOnSlaves.contains(slaveState.getSlaveIndex())) {
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
      if (stressorConfiguration.useLogValues) {
         startCheckers();
      }
      if (stressorConfiguration.ignoreDeadCheckers) {
         keepAliveThread = new KeepAliveThread();
         keepAliveThread.start();
      }
   }

   private synchronized void startStressorThreads() {
      stressorThreads = new Stressor[stressorConfiguration.numThreads];
      if (stressorConfiguration.numThreads <= 0) {
         log.warn("Stressor thread number set to 0!");
         return;
      }
      for (int i = 0; i < stressorThreads.length; i++) {
         stressorThreads[i] = new Stressor(this, createLogic(i), i);
         stressorThreads[i].setLoaded(this.loaded);
         stressorThreads[i].start();
      }
   }

   private Logic createLogic(int index) {
      if (stressorConfiguration.sharedKeys) {
         if (stressorConfiguration.useLogValues) {
            return new SharedLogLogic(this, index, stressorConfiguration.numEntries);
         } else {
            throw new IllegalArgumentException("Legacy logic cannot use shared keys.");
         }
      } else {
         int numThreads = stressorConfiguration.numThreads;
         int totalThreads = numThreads * slaveState.getClusterSize();
         Range range = Range.divideRange(stressorConfiguration.numEntries, totalThreads, numThreads * slaveState.getSlaveIndex() + index);
         if (stressorConfiguration.useLogValues) {
            return new PrivateLogLogic(this, index, range);
         } else {
            List<Integer> deadSlaves = stressorConfiguration.loadDataForDeadSlaves;
            List<List<Range>> rangesForThreads = null;
            int liveId = slaveState.getSlaveIndex();
            if (!loaded && deadSlaves != null && !deadSlaves.isEmpty()) {
               List<Range> deadSlavesKeyRanges = new ArrayList<Range>(deadSlaves.size() * numThreads);
               for (int deadSlave : deadSlaves) {
                  // key ranges for the current dead slave
                  for (int deadThread = 0; deadThread < numThreads; ++deadThread) {
                     deadSlavesKeyRanges.add(Range.divideRange(stressorConfiguration.numEntries, totalThreads, deadSlave * numThreads + deadThread));
                  }
                  if (deadSlave < slaveState.getSlaveIndex()) liveId--;
               }
               rangesForThreads = Range.balance(deadSlavesKeyRanges, (slaveState.getClusterSize() - deadSlaves.size()) * numThreads);
            }
            List<Range> deadRanges = rangesForThreads == null ? null : rangesForThreads.get(index + numThreads * liveId);
            return new LegacyLogic(this, range, deadRanges);
         }
      }
   }

   private synchronized void startCheckers() {
      if (stressorConfiguration.logCheckingThreads <= 0) {
         log.error("LogValue checker set to 0!");
      } else if (stressorConfiguration.sharedKeys) {
         logCheckers = new LogChecker[stressorConfiguration.logCheckingThreads];
         SharedLogChecker.Pool pool = new SharedLogChecker.Pool(
               slaveState.getClusterSize(), stressorConfiguration.numThreads, stressorConfiguration.numEntries, this);
         logCheckerPool = pool;
         for (int i = 0; i < stressorConfiguration.logCheckingThreads; ++i) {
            logCheckers[i] = new SharedLogChecker(i, pool, this);
            logCheckers[i].start();
         }
      } else {
         logCheckers = new LogChecker[stressorConfiguration.logCheckingThreads];
         PrivateLogChecker.Pool pool = new PrivateLogChecker.Pool(
               slaveState.getClusterSize(), stressorConfiguration.numThreads, stressorConfiguration.numEntries, this);
         logCheckerPool = pool;
         for (int i = 0; i < stressorConfiguration.logCheckingThreads; ++i) {
            logCheckers[i] = new PrivateLogChecker(i, pool, this);
            logCheckers[i].start();
         }
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
      if (stressorThreads == null) {
         return;
      }
      boolean loaded = false;
      while (!loaded) {
         loaded = true;
         for (Stressor st : stressorThreads) {
            if (!st.isLoaded()) {
               loaded = false;
            }
         }
         if (!loaded) {
            Thread.sleep(100);
         }
      }
   }

   public String waitUntilChecked() {
      if (logCheckerPool == null || logCheckers == null) {
         log.warn("No log checker pool or active checkers.");
         return null;
      }
      Stressor[] stressors = stressorThreads;
      if (stressors != null) {
         stopBackgroundThreads(true, false, false);
      }
      String error = logCheckerPool.waitUntilChecked(stressorConfiguration.logCheckersNoProgressTimeout);
      if (error != null) {
         return error;
      }
      return null;
   }

   public void resumeAfterChecked() {
      if (stressorThreads == null) {
         startStressorThreads();
      } else {
         log.error("Stressors already started.");
      }
   }

   /**
    * 
    * Stops the stressors, call this before tearing down or killing CacheWrapper.
    * 
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
      if (checkers && logCheckers != null) {
         for (int i = 0; i < logCheckers.length; ++i) {
            logCheckers[i].requestTerminate();
         }
      }
      if (keepAlive && keepAliveThread != null) {
         keepAliveThread.requestTerminate();
      }
      // give the threads a second to terminate
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {         
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
      if (keepAlive && keepAliveThread != null) {
         keepAliveThread.interrupt();
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
         if (keepAlive && keepAliveThread != null) {
            keepAliveThread.join();
         }
         log.debug("All threads have joined");
      } catch (InterruptedException e1) {
         log.error("interrupted while waiting for sizeThread and stressorThreads to stop");
      }
      if (stressors) stressorThreads = null;
      if (checkers) logCheckers = null;
      if (keepAlive) keepAliveThread = null;
   }

   public synchronized void startStats() {
      if (stats == null) {
         stats = new ArrayList<List<Statistics>>();
      }
      if (sizeThread == null) {
         sizeThread = new SizeThread();
         sizeThread.start();
      }
      if (backgroundStatsThread == null) {
         backgroundStatsThread = new BackgroundStatsThread();
         backgroundStatsThread.start();
      }
   }

   public synchronized List<List<Statistics>> stopStats() {
      if (backgroundStatsThread == null || stats == null) {
         throw new IllegalStateException("Stat thread not running");
      }
      log.debug("Interrupting statistics threads");
      backgroundStatsThread.interrupt();
      sizeThread.interrupt();
      try {
         backgroundStatsThread.join();
         sizeThread.join();
      } catch (InterruptedException e) {
         log.error("Interrupted while waiting for stat thread to end.");
      }
      List<List<Statistics>> statsToReturn = stats;
      stats = null;
      return statsToReturn;
   }



   public KeyGenerator getKeyGenerator() {
      KeyGenerator keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
      if (keyGenerator == null) {
         keyGenerator = new StringKeyGenerator();
         slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      }
      return keyGenerator;
   }

   public String getError() {
      if (stressorConfiguration.useLogValues) {
         if (logCheckerPool != null && logCheckerPool.getMissingOperations() > 0) {
            return "Background stressors report " + logCheckerPool.getMissingOperations() + " missing operations!";
         }
         if (logCheckers != null) {
            long lastProgress = System.currentTimeMillis() - logCheckerPool.getLastStoredOperationTimestamp();
            if (lastProgress > stressorConfiguration.logCheckersNoProgressTimeout) {
               StringBuilder sb = new StringBuilder(1000).append("Current stressors info:\n");
               for (Stressor stressor : stressorThreads) {
                  sb.append(stressor.getStatus()).append(", stacktrace:\n");
                  for (StackTraceElement ste : stressor.getStackTrace()) {
                     sb.append(ste).append("\n");
                  }
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
               return "No progress in checkers for " + lastProgress + " ms!";
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
      return value != null &&  value instanceof Long && ((Long) value) > System.currentTimeMillis() - stressorConfiguration.deadSlaveTimeout;
   }

   public BasicOperations.Cache getBasicCache() {
      return basicCache;
   }

   public Debugable.Cache getDebugableCache() {
      return debugableCache;
   }

   public Transactional.Resource getTransactionalCache() {
      return transactionalCache;
   }

   public ConditionalOperations.Cache getConditionalCache() {
      return conditionalCache;
   }

   private class KeepAliveThread extends Thread {
      private volatile boolean terminate;

      private KeepAliveThread() {
         super("KeepAlive");
      }

      @Override
      public void run() {
         while (!terminate && !isInterrupted()) {
            try {
               basicCache.put("__keepAlive_" + getSlaveIndex(), System.currentTimeMillis());
            } catch (Exception e) {
               log.error("Failed to place keep alive timestamp", e);
            }
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               log.error("Interrupted", e);
               break;
            }
         }
      }

      public void requestTerminate() {
         terminate = true;
      }
   }

   private class BackgroundStatsThread extends Thread {
      public BackgroundStatsThread() {
         super("BackgroundStatsThread");
      }

      public void run() {
         try {
            gatherStats(); // throw away first stats
            while (true) {
               sleep(statsIteration);
               stats.add(gatherStats());
            }
         } catch (InterruptedException e) {
            log.trace("Stressor interrupted.");
         }
      }

      private List<Statistics> gatherStats() {
         Stressor[] threads = stressorThreads;
         if (threads == null) {
            return Collections.EMPTY_LIST;
         } else {
            List<Statistics> stats = new ArrayList<Statistics>(threads.length);
            for (int i = 0; i < threads.length; i++) {
               if (threads[i] != null) {
                  stats.add(threads[i].getStatsSnapshot(true));
               }
            }
            slaveState.getTimeline().addValue(CACHE_SIZE, new Timeline.Value(sizeThread.getAndResetSize()));
            return stats;
         }
      }
   }

   /**
    * 
    * Used for fetching cache size. If the size can't be fetched during one stat iteration, value 0
    * will be used.
    * 
    */
   private class SizeThread extends Thread {
      public SizeThread() {
         super("SizeThread");
      }

      private boolean getSize = true;
      private int size = -1;

      @Override
      public void run() {
         try {
            while (!isInterrupted()) {
               synchronized (this) {
                  while (!getSize) {
                     wait(100);
                  }
                  getSize = false;
               }
               if (cacheInfo != null && lifecycle.isRunning()) {
                  size = cacheInfo.getLocalSize();
               } else {
                  size = 0;
               }
            }
         } catch (InterruptedException e) {
            log.trace("SizeThread interrupted.");
         }
      }

      public synchronized int getAndResetSize() {
         int rSize = size;
         size = -1;
         getSize = true;
         notify();
         return rSize;
      }
   }

   public static void beforeCacheWrapperDestroy(SlaveState slaveState, boolean destroyAll) {
      BackgroundOpsManager instance = getInstance(slaveState);
      if (instance != null) {
         instance.stopBackgroundThreads();
         if (destroyAll) {
            instance.destroy();
         }
      }
   }

   public static void afterCacheWrapperStart(SlaveState slaveState) {
      BackgroundOpsManager instance = getInstance(slaveState);
      if (instance != null) {
         instance.setLoaded(true); // don't load data at this stage
         instance.loadCaches();
         instance.startBackgroundThreads();
      }
   }

   public static void beforeCacheWrapperClear(SlaveState slaveState) {
      BackgroundOpsManager instance = BackgroundOpsManager.getInstance(slaveState);
      if (instance != null) {
         instance.setLoaded(false);
      }
   }

   public void setLoaded(boolean loaded) {
      this.loaded = loaded;
   }

   public int getSlaveIndex() {
      return slaveState.getSlaveIndex();
   }

   public int getClusterSize() {
      return slaveState.getClusterSize();
   }

   public int getNumThreads() {
      return stressorConfiguration.numThreads;
   }

   public boolean isIgnoreDeadCheckers() {
      return stressorConfiguration.ignoreDeadCheckers;
   }

   public boolean getLoadOnly() {
      return stressorConfiguration.loadOnly;
   }

   public boolean getLoadWithPutIfAbsent() {
      return stressorConfiguration.loadWithPutIfAbsent;
   }

   public int getLogValueMaxSize() {
      return stressorConfiguration.logValueMaxSize;
   }

   public long getLogCounterUpdatePeriod() {
      return stressorConfiguration.logCounterUpdatePeriod;
   }
   
   public int getTransactionSize() {
      return stressorConfiguration.transactionSize;
   }

   public int getEntrySize() {
      return stressorConfiguration.entrySize;
   }

   public long getDelayBetweenRequests() {
      return stressorConfiguration.delayBetweenRequests;
   }
}
