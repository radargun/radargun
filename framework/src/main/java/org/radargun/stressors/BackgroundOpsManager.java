package org.radargun.stressors;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
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

   private static Logger log = Logger.getLogger(BackgroundOpsManager.class);

   private int puts;
   private int gets;
   private int removes;
   private int operations;
   
   private int numEntries;
   private int entrySize;
   private int numThreads;
   private SlaveState slaveState;
   private long delayBetweenRequests;
   private BackgroundStressor[] stressorThreads;
   private SizeThread sizeThread;
   private volatile boolean stressorsRunning = false;
   private int numSlaves;
   private int slaveIndex;
   private List<Statistics> stats;
   private BackgroundStatsThread backgroundStatsThread;
   private long statsIteration;
   private boolean loaded = false;
   private int transactionSize;
   private List<Integer> loadDataForDeadSlaves;
   private String bucketId;

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
                                                          int transactionSize, List<Integer> loadDataForDeadSlaves) {
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
      instance.loadDataForDeadSlaves = loadDataForDeadSlaves;
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

   public synchronized void startStressors() {
      if (stressorThreads != null || stressorsRunning) {
         log.warn("Can't start stressors, they're already running.");
         return;
      }
      if (slaveState.getCacheWrapper() == null) {
         log.warn("Can't start stressors, cache wrapper not available");
         return;
      }
      stressorThreads = new BackgroundStressor[numThreads];
      if (numThreads > 0) {
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
                  rangesForThreads == null ? null : rangesForThreads.get(i + numThreads * liveId), i);
            stressorThreads[i].setLoaded(this.loaded);
            stressorThreads[i].start();
         }
      } else {
         log.warn("Stressor thread number set to 0!");
      }
      stressorsRunning = true;
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

   /**
    * 
    * Stops the stressors, call this before tearing down or killing CacheWrapper.
    * 
    */
   public synchronized void stopStressors() {
      if (stressorThreads == null || !stressorsRunning) {
         log.warn("Can't stop stressors, they're not running.");
         return;
      }
      stressorsRunning = false;
      log.debug("Interrupting size thread");
      // interrupt all threads
      log.debug("Stopping stressors");
      for (int i = 0; i < stressorThreads.length; i++) {
         stressorThreads[i].requestTerminate();
      }
      // give the threads a second to terminate
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {         
      }
      log.debug("Interrupting stressors");
      for (int i = 0; i < stressorThreads.length; i++) {
         stressorThreads[i].interrupt();
      }
      log.debug("Waiting until all threads join");
      // then wait for them to finish
      try {
         for (int i = 0; i < stressorThreads.length; i++) {
            stressorThreads[i].join();
         }
         log.debug("All threads have joined");
      } catch (InterruptedException e1) {
         log.error("interrupted while waiting for sizeThread and stressorThreads to stop");
      }
      stressorThreads = null;
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
         if (!stressorsRunning) {
            return nodeDownStats.snapshot(true, now);
         } else {
            nodeDownStats.reset(now); // we need to reset should we need them in next round
            SynchronizedStatistics r = null;
            for (int i = 0; i < stressorThreads.length; i++) {
               SynchronizedStatistics threadStats = stressorThreads[i].getStatsSnapshot(true, now);
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
         instance.stopStressors();
         if (destroyAll) {
            instance.destroy();
         }
      }
   }

   public static void afterCacheWrapperStart(SlaveState slaveState) {
      BackgroundOpsManager instance = getInstance(slaveState);
      if (instance != null) {
         instance.setLoaded(); // don't load data at this stage
         instance.startStressors();
      }
   }

   public void setLoaded() {
      loaded = true;
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

   public CacheWrapper getCacheWrapper() {
      return slaveState.getCacheWrapper();
   }
}
