package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Timeline;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.helpers.Range;
import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Throughput;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.CacheListeners;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Debugable;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.Transactional;
import org.radargun.utils.Utils;

/**
 * 
 * Manages background statistics collectors and stressors. The particular stressing strategy
 * is specified by Logic class implementation.
 *
 * @See LegacyLogic
 * @See PrivateLogLogic
 * @See SharedLogLogic
 *
 * //TODO: more polishing to make this class agnostic to implemented logic (just pass configuration)
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BackgroundOpsManager implements ServiceListener {
   /**
    * Key to SlaveState to retrieve BackgroundOpsManager instance and to MasterState to retrieve results.
    */
   public static final String NAME = "BackgroundOpsManager";
   private static final String CACHE_SIZE = "Cache size";

   private static Log log = LogFactory.getLog(BackgroundOpsManager.class);

   private GeneralConfiguration generalConfiguration;
   private LegacyLogicConfiguration legacyLogicConfiguration;
   private LogLogicConfiguration logLogicConfiguration;
   private int operations;

   private SlaveState slaveState;
   private volatile Stressor[] stressorThreads;
   private SizeThread sizeThread;
   private ScheduledFuture statsTask;
   private ScheduledFuture keepAliveTask;
   private ScheduledExecutorService executor;
   private List<List<Statistics>> stats;
   private long statsIterationDuration;
   private boolean loaded = false;
   private LogChecker[] logCheckers;
   private LogChecker.Pool logCheckerPool;

   private Lifecycle lifecycle;
   private CacheListeners listeners;
   private volatile BasicOperations.Cache basicCache;
   private volatile Debugable.Cache debugableCache;
   private volatile Transactional.Resource transactionalCache;
   private volatile ConditionalOperations.Cache conditionalCache;
   private volatile CacheInformation.Cache cacheInfo;

   public static BackgroundOpsManager getInstance(SlaveState slaveState) {
      return (BackgroundOpsManager) slaveState.get(NAME);
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState) {
      BackgroundOpsManager instance = getInstance(slaveState);
      if (instance == null) {
         instance = new BackgroundOpsManager();
         instance.slaveState = slaveState;
         instance.executor = Executors.newScheduledThreadPool(2);
         slaveState.put(NAME, instance);
         slaveState.addServiceListener(instance);
      }
      return instance;
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState,
                                                          GeneralConfiguration generalConfiguration,
                                                          LegacyLogicConfiguration legacyLogicConfiguration,
                                                          LogLogicConfiguration logLogicConfiguration) {
      BackgroundOpsManager instance = getOrCreateInstance(slaveState);
      instance.generalConfiguration = generalConfiguration;
      instance.legacyLogicConfiguration = legacyLogicConfiguration;
      instance.logLogicConfiguration = logLogicConfiguration;
      instance.operations = generalConfiguration.puts + generalConfiguration.gets + generalConfiguration.removes;

      instance.lifecycle = slaveState.getTrait(Lifecycle.class);
      instance.listeners = slaveState.getTrait(CacheListeners.class);
      instance.loadCaches();
      return instance;
   }

   public static BackgroundOpsManager getOrCreateInstance(SlaveState slaveState, long statsIterationDuration) {
      BackgroundOpsManager instance = getOrCreateInstance(slaveState);
      instance.statsIterationDuration = statsIterationDuration;
      return instance;
   }

   private void loadCaches() {
      basicCache = slaveState.getTrait(BasicOperations.class).getCache(generalConfiguration.cacheName);
      ConditionalOperations conditionalOperations = slaveState.getTrait(ConditionalOperations.class);
      conditionalCache = conditionalOperations == null ? null : conditionalOperations.getCache(generalConfiguration.cacheName);
      Debugable debugable = slaveState.getTrait(Debugable.class);
      debugableCache = debugable == null ? null : debugable.getCache(generalConfiguration.cacheName);
      if (generalConfiguration.transactionSize > 0) {
         Transactional transactional = slaveState.getTrait(Transactional.class);
         if (transactional == null) {
            throw new IllegalArgumentException("Transactions are set on but the service does not provide transactions");
         } else if (!transactional.isTransactional(generalConfiguration.cacheName)) {
            throw new IllegalArgumentException("Transactions are set on but the cache is not configured as transactional");
         }
         transactionalCache = transactional.getResource(generalConfiguration.cacheName);
      }
      CacheInformation cacheInformation = slaveState.getTrait(CacheInformation.class);
      cacheInfo = cacheInformation == null ? null : cacheInformation.getCache(generalConfiguration.cacheName);
   }

   private void unloadCaches() {
      basicCache = null;
      conditionalCache = null;
      debugableCache = null;
      transactionalCache = null;
      cacheInfo = null;
   }

   private BackgroundOpsManager() {
   }

   public Operation getOperation(Random rand) {
      int r = rand.nextInt(operations);
      if (r < generalConfiguration.gets) {
         return BasicOperations.GET;
      } else if (r < generalConfiguration.gets + generalConfiguration.puts) {
         return BasicOperations.PUT;
      } else return BasicOperations.REMOVE;
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
         startCheckers();
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
            return new SharedLogLogic(this, numThreads * slaveState.getSlaveIndex() * index, generalConfiguration.numEntries);
         } else {
            throw new IllegalArgumentException("Legacy logic cannot use shared keys.");
         }
      } else {
         int totalThreads = numThreads * slaveState.getClusterSize();
         Range range = Range.divideRange(generalConfiguration.numEntries, totalThreads, numThreads * slaveState.getSlaveIndex() + index);

         if (logLogicConfiguration.enabled) {
            return new PrivateLogLogic(this, numThreads * slaveState.getSlaveIndex() + index, range);
         } else {
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

   private synchronized void startCheckers() {
      if (logLogicConfiguration.checkingThreads <= 0) {
         log.error("LogValue checker set to 0!");
      } else if (generalConfiguration.sharedKeys) {
         logCheckers = new LogChecker[logLogicConfiguration.checkingThreads];
         SharedLogChecker.Pool pool = new SharedLogChecker.Pool(
               slaveState.getClusterSize(), generalConfiguration.numThreads, generalConfiguration.numEntries, this);
         logCheckerPool = pool;
         for (int i = 0; i < logLogicConfiguration.checkingThreads; ++i) {
            logCheckers[i] = new SharedLogChecker(i, pool, this);
            logCheckers[i].start();
         }
      } else {
         logCheckers = new LogChecker[logLogicConfiguration.checkingThreads];
         PrivateLogChecker.Pool pool = new PrivateLogChecker.Pool(
               slaveState.getClusterSize(), generalConfiguration.numThreads, generalConfiguration.numEntries, this);
         logCheckerPool = pool;
         for (int i = 0; i < logLogicConfiguration.checkingThreads; ++i) {
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
      if (logCheckerPool == null || logCheckers == null) {
         log.warn("No log checker pool or active checkers.");
         return null;
      }
      Stressor[] stressors = stressorThreads;
      if (stressors != null) {
         stopBackgroundThreads(true, false, false);
      }
      String error = logCheckerPool.waitUntilChecked(logLogicConfiguration.checkersNoProgressTimeout);
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
         for (int i = 0; i < stressorThreads.length; i++) {
            stressorThreads[i].requestTerminate();
         }
      }
      if (checkers && logCheckers != null) {
         for (int i = 0; i < logCheckers.length; ++i) {
            logCheckers[i].requestTerminate();
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

   public synchronized void startStats() {
      if (stats == null) {
         stats = new ArrayList<List<Statistics>>();
      }
      if (sizeThread == null) {
         sizeThread = new SizeThread();
         sizeThread.start();
      }
      if (statsTask == null) {
         statsTask = executor.scheduleAtFixedRate(new StatsTask(), 0, statsIterationDuration, TimeUnit.MILLISECONDS);
      }
   }

   public synchronized List<List<Statistics>> stopStats() {
      if (statsTask != null) {
         statsTask.cancel(true);
         statsTask = null;
      }
      if (sizeThread != null) {
         log.debug("Interrupting size thread");
         sizeThread.interrupt();
         try {
            sizeThread.join();
         } catch (InterruptedException e) {
            log.error("Interrupted while waiting for stat thread to end.");
         }
         sizeThread = null;
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
      if (logLogicConfiguration.enabled) {
         if (logCheckerPool != null && (logCheckerPool.getMissingOperations() > 0 || logCheckerPool.getMissingNotifications() > 0)) {
            return String.format("Background stressors report %d missing operations and %d missing notifications!",
                  logCheckerPool.getMissingOperations(), logCheckerPool.getMissingNotifications());
         }
         if (logCheckers != null) {
            long lastProgress = System.currentTimeMillis() - logCheckerPool.getLastStoredOperationTimestamp();
            if (lastProgress > logLogicConfiguration.checkersNoProgressTimeout) {
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
      return value != null &&  value instanceof Long && ((Long) value) > System.currentTimeMillis() - generalConfiguration.deadSlaveTimeout;
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

   public CacheListeners getListeners() {
      return listeners;
   }

   // TODO:
   // a) clear listener on the BasicOperations trait
   // b) deal with the fact that the clear can be executed
   public static void beforeCacheClear(SlaveState slaveState) {
      BackgroundOpsManager instance = BackgroundOpsManager.getInstance(slaveState);
      if (instance != null) {
         instance.setLoaded(false);
      }
   }

   @Override
   public void beforeServiceStart() {}

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

   @Override
   public void serviceDestroyed() {
      stopStats();
      slaveState.remove(NAME);
      slaveState.removeServiceListener(this);
   }

   private class KeepAliveTask implements Runnable {
      @Override
      public void run() {
         try {
            basicCache.put("__keepAlive_" + getSlaveIndex(), System.currentTimeMillis());
         } catch (Exception e) {
            log.error("Failed to place keep alive timestamp", e);
         }
      }
   }

   private class StatsTask implements Runnable {
      StatsTask() {
         gatherStats(); // throw away first stats
      }

      public void run() {
         stats.add(gatherStats());
      }

      private List<Statistics> gatherStats() {
         Stressor[] threads = stressorThreads;
         List<Statistics> stats;
         if (threads == null) {
            stats = Collections.EMPTY_LIST;
         } else {
            stats = new ArrayList<Statistics>(threads.length);
            for (int i = 0; i < threads.length; i++) {
               if (threads[i] != null) {
                  stats.add(threads[i].getStatsSnapshot(true));
               }
            }
         }
         Timeline timeline = slaveState.getTimeline();
         long now = System.currentTimeMillis();
         timeline.addValue(CACHE_SIZE, new Timeline.Value(now, sizeThread.getAndResetSize()));
         if (stats.isEmpty()) {
            // add zero for all operations we've already reported
            for (String valueCategory : timeline.getValueCategories()) {
               if (valueCategory.endsWith(" Throughput")) {
                  timeline.addValue(valueCategory, new Timeline.Value(now, 0));
               }
            }
         } else {
            Statistics aggregated = stats.get(0).copy();
            for (int i = 1; i < stats.size(); ++i) {
               aggregated.merge(stats.get(i));
            }
            boolean anyRequests = false;
            for (Map.Entry<String, OperationStats> entry : aggregated.getOperationsStats().entrySet()) {
               DefaultOutcome defaultOutcome = entry.getValue().getRepresentation(DefaultOutcome.class);
               if (defaultOutcome.requests != 0 || timeline.getValues(entry.getKey() + " Throughput") != null) {
                  Throughput throughput = defaultOutcome.toThroughput(
                        stats.size(), TimeUnit.MILLISECONDS.toNanos(aggregated.getEnd() - aggregated.getBegin()));
                  timeline.addValue(entry.getKey() + " Throughput", new Timeline.Value(now, throughput.actual));
               }
               if (entry.getValue().getRepresentation(DefaultOutcome.class).requests > 0) anyRequests = true;
            }
            if (!anyRequests) {
               Utils.threadDump();
            }
         }
         log.trace("Adding iteration " + BackgroundOpsManager.this.stats.size() + ": " + stats);
         return stats;
      }
   }

   /**
    * 
    * Used for fetching cache size. If the size can't be fetched during one stat iteration, value 0
    * will be used.
    * 
    */
   private class SizeThread extends Thread {
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

   public void setLoaded(boolean loaded) {
      this.loaded = loaded;
   }

   public int getSlaveIndex() {
      return slaveState.getSlaveIndex();
   }

   public int getClusterSize() {
      return slaveState.getClusterSize();
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
}
