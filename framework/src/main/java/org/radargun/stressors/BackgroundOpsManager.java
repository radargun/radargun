package org.radargun.stressors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;

/**
 * 
 * Implements background statistics collectors and stressors. BackgroundOpsManager don't stress the cache
 * to the fullest, they just apply mild continuous load (with wait between requests) to have some
 * statistics about cache throughput during resilience/elasticity tests.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 * 
 */
public class BackgroundOpsManager {
   /**
    * Bucket name for CacheWrapper requests, also key to SlaveState to retrieve BackgroundOpsManager
    * instance and to MasterState to retrieve results.
    */
   public static final String NAME = "BackgroundOpsManager";

   private static Log log = LogFactory.getLog(BackgroundOpsManager.class);

   private int puts;
   private int gets;
   private int removes;
   private int operations;
   private int numEntries;
   private int entrySize;
   private String bucketId;
   private int numThreads;
   private long delayBetweenRequests;
   private int numSlaves;
   private int slaveIndex;
   private int transactionSize;
   private List<Integer> loadDataOnSlaves;
   private List<Integer> loadDataForDeadSlaves;
   private boolean loadOnly;
   private boolean loadWithPutIfAbsent;
   private boolean useLogValues;
   private int logCheckingThreads;
   private int logValueMaxSize;
   private long logCounterUpdatePeriod;
   private long logCheckersNoProgressTimeout;
   private boolean sharedKeys;

   private SlaveState slaveState;
   private volatile BackgroundStressor[] stressorThreads;
   private SizeThread sizeThread;
   private KeepAliveThread keepAliveThread;
   private List<Statistics> stats;
   private BackgroundStatsThread backgroundStatsThread;
   private long statsIteration;
   private boolean loaded = false;
   private LogChecker[] logCheckers;
   private LogChecker.Pool logCheckerPool;
   private boolean ignoreDeadCheckers;
   private long deadSlaveTimeout;

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

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState, int puts, int gets, int removes,
                                                          int numEntries, int entrySize, String bucketId, int numThreads,
                                                          long delayBetweenRequests, int numSlaves, int slaveIndex,
                                                          int transactionSize, List<Integer> loadDataOnSlaves,
                                                          List<Integer> loadDataForDeadSlaves, boolean loadOnly,
                                                          boolean loadWithPutIfAbsent, boolean useLogValues, int logCheckingThreads,
                                                          int logValueMaxSize, long logCounterUpdatePeriod,
                                                          long logCheckersNoProgressTimeout, boolean ignoreDeadCheckers,
                                                          long deadSlaveTimeout, boolean sharedKeys) {
      BackgroundOpsManager instance = getOrCreateInstance(slaveState);
      instance.puts = puts;
      instance.gets = gets;
      instance.removes = removes;
      instance.operations = puts + gets + removes;
      instance.numEntries = numEntries;
      instance.entrySize = entrySize;
      instance.bucketId = bucketId;
      instance.numThreads = numThreads;
      instance.delayBetweenRequests = delayBetweenRequests;
      instance.numSlaves = numSlaves;
      instance.slaveIndex = slaveIndex;
      instance.transactionSize = transactionSize;
      instance.loadDataOnSlaves = loadDataOnSlaves;
      instance.loadDataForDeadSlaves = loadDataForDeadSlaves;
      instance.loadOnly = loadOnly;
      instance.loadWithPutIfAbsent = loadWithPutIfAbsent;
      instance.useLogValues = useLogValues;
      instance.logCheckingThreads = logCheckingThreads;
      instance.logValueMaxSize = logValueMaxSize;
      instance.logCounterUpdatePeriod = logCounterUpdatePeriod;
      instance.logCheckersNoProgressTimeout = logCheckersNoProgressTimeout;
      instance.ignoreDeadCheckers = ignoreDeadCheckers;
      instance.deadSlaveTimeout = deadSlaveTimeout;
      instance.sharedKeys = sharedKeys;
      return instance;
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
      if (r < gets) {
         return Operation.GET;
      } else if (r < gets + puts) {
         return Operation.PUT;
      } else return Operation.REMOVE;
   }

   public synchronized void startBackgroundThreads() {
      if (loadDataOnSlaves != null && !loadDataOnSlaves.isEmpty() && !loadDataOnSlaves.contains(slaveIndex)) {
         log.info("This slave is not loading any data.");
         return;
      }
      if (stressorThreads != null) {
         log.warn("Can't start stressors, they're already running.");
         return;
      }
      if (slaveState.getCacheWrapper() == null) {
         log.warn("Can't start stressors, cache wrapper not available");
         return;
      }
      startStressorThreads();
      if (useLogValues) {
         startCheckers();
      }
      if (ignoreDeadCheckers) {
         keepAliveThread = new KeepAliveThread();
         keepAliveThread.start();
      }
   }

   private synchronized void startStressorThreads() {
      stressorThreads = new BackgroundStressor[numThreads];
      if (numThreads <= 0) {
         log.warn("Stressor thread number set to 0!");
         return;
      }
      Range slaveKeyRange = Range.divideRange(numEntries, numSlaves, slaveIndex);

      List<List<Range>> rangesForThreads = null;
      int liveId = slaveIndex;
      if (!loaded && loadDataForDeadSlaves != null && !loadDataForDeadSlaves.isEmpty()) {
         List<Range> deadSlavesKeyRanges = new ArrayList<Range>(loadDataForDeadSlaves.size());
         for (int deadSlave : loadDataForDeadSlaves) {
            deadSlavesKeyRanges.add(Range.divideRange(numEntries, numSlaves, deadSlave)); // key range for the current dead slave
            if (deadSlave < slaveIndex) liveId--;
         }
         rangesForThreads = Range.balance(deadSlavesKeyRanges, (numSlaves - loadDataForDeadSlaves.size()) * numThreads);
      }

      for (int i = 0; i < stressorThreads.length; i++) {
         Range threadKeyRange = Range.divideRange(slaveKeyRange.getSize(), numThreads, i);
         Range myKeyRange = threadKeyRange.shift(slaveKeyRange.getStart());
         stressorThreads[i] = new BackgroundStressor(this, slaveState, myKeyRange,
               rangesForThreads == null ? null : rangesForThreads.get(i + numThreads * liveId), slaveIndex, i);
         stressorThreads[i].setLoaded(this.loaded);
         stressorThreads[i].start();
      }
   }

   private synchronized void startCheckers() {
      if (logCheckingThreads <= 0) {
         log.error("LogValue checker set to 0!");
      } else if (sharedKeys) {
         logCheckers = new LogChecker[logCheckingThreads];
         SharedLogChecker.Pool pool = new SharedLogChecker.Pool(numSlaves, numThreads, numEntries, this);
         logCheckerPool = pool;
         for (int i = 0; i < logCheckingThreads; ++i) {
            logCheckers[i] = new SharedLogChecker(i, pool, this);
            logCheckers[i].start();
         }
      } else {
         logCheckers = new LogChecker[logCheckingThreads];
         PrivateLogChecker.Pool pool = new PrivateLogChecker.Pool(numSlaves, numThreads, numEntries, this);
         logCheckerPool = pool;
         for (int i = 0; i < logCheckingThreads; ++i) {
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
         for (BackgroundStressor st : stressorThreads) {
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
      BackgroundStressor[] stressors = stressorThreads;
      if (stressors != null) {
         stopBackgroundThreads(true, false, false);
      }
      String error = logCheckerPool.waitUntilChecked(logCheckersNoProgressTimeout);
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
         stats = new ArrayList<Statistics>();
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

   public synchronized List<Statistics> stopStats() {
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
      List<Statistics> statsToReturn = stats;
      stats = null;
      return statsToReturn;
   }

   public boolean getLoadOnly() {
      return loadOnly;
   }

   public boolean getLoadWithPutIfAbsent() {
      return loadWithPutIfAbsent;
   }

   public int getSlaveIndex() {
      return slaveIndex;
   }

   public int getNumSlaves() {
      return numSlaves;
   }

   public int getLogCheckingThreads() {
      return logCheckingThreads;
   }

   public int getLogValueMaxSize() {
      return logValueMaxSize;
   }

   public long getLogCounterUpdatePeriod() {
      return logCounterUpdatePeriod;
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
      if (useLogValues) {
         if (logCheckerPool != null && logCheckerPool.getMissingOperations() > 0) {
            return "Background stressors report " + logCheckerPool.getMissingOperations() + " missing operations!";
         }
         if (logCheckers != null) {
            long lastProgress = System.currentTimeMillis() - logCheckerPool.getLastStoredOperationTimestamp();
            if (lastProgress > logCheckersNoProgressTimeout) {
               StringBuilder sb = new StringBuilder(1000).append("Current stressors info:\n");
               for (BackgroundStressor stressor : stressorThreads) {
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

   public int getNumThreads() {
      return numThreads;
   }

   public int getNumEntries() {
      return numEntries;
   }

   public boolean isIgnoreDeadCheckers() {
      return ignoreDeadCheckers;
   }

   public boolean isSlaveAlive(int slaveId) {
      Object value = null;
      try {
         value = getCacheWrapper().get(getBucketId(), "__keepAlive_" + slaveId);
      } catch (Exception e) {
         log.error("Failed to retrieve the keep alive timestamp", e);
         return true;
      }
      return value != null &&  value instanceof Long && ((Long) value) > System.currentTimeMillis() - deadSlaveTimeout;
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
               getCacheWrapper().put(getBucketId(), "__keepAlive_" + getSlaveIndex(), System.currentTimeMillis());
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

      private SynchronizedStatistics nodeDownStats = new SynchronizedStatistics(false);

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

      private SynchronizedStatistics gatherStats() {
         long now = System.currentTimeMillis();
         BackgroundStressor[] threads = stressorThreads;
         if (threads == null) {
            return nodeDownStats.snapshot(true, now);
         } else {
            nodeDownStats.reset(now); // we need to reset should we need them in next round
            SynchronizedStatistics r = null;
            for (int i = 0; i < threads.length; i++) {
               SynchronizedStatistics threadStats = threads[i].getStatsSnapshot(true, now);
               if (r == null) {
                  r = threadStats;
               } else {
                  r.merge(threadStats);
               }
            }
            if (r != null) {
               r.cacheSize = sizeThread.getAndResetSize();
            }
            return r;
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
               CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
               if (cacheWrapper != null && cacheWrapper.isRunning()) {
                  size = slaveState.getCacheWrapper().getLocalSize();
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
   
   public String getBucketId() {
      return bucketId;
   }

   public int getTransactionSize() {
      return transactionSize;
   }

   public int getEntrySize() {
      return entrySize;
   }

   public long getDelayBetweenRequests() {
      return delayBetweenRequests;
   }

   public boolean isUseLogValues() {
      return useLogValues;
   }

   public boolean isSharedKeys() {
      return sharedKeys;
   }

   public CacheWrapper getCacheWrapper() {
      return slaveState.getCacheWrapper();
   }
}
