package org.radargun.stressors;

import org.apache.log4j.Logger;
import org.radargun.CacheWrapper;
import org.radargun.stages.helpers.RangeHelper;
import org.radargun.state.SlaveState;

import java.io.Serializable;
import java.util.*;

/**
 * 
 * Implements background statistics collectors and stressors. BackgroundStats don't stress the cache
 * to the fullest, they just apply mild continuous load (with wait between requests) to have some
 * statistics about cache throughput during resilience/elasticity tests.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 * 
 */
public class BackgroundStats {
   /**
    * Bucket name for CacheWrapper requests, also key to SlaveState to retrieve BackgroundStats
    * instance and to MasterState to retrieve results.
    */
   public static final String NAME = "BackgroundStats";

   private enum Operation {
      GET, PUT, REMOVE
   }
   
   private static Logger log = Logger.getLogger(BackgroundStats.class);
   private static Random r = new Random();

   private int puts;
   private int gets;
   private int removes;
   private int operations;
   
   private int numEntries;
   private int entrySize;
   private int numThreads;
   private SlaveState slaveState;
   private long delayBetweenRequests;
   private StressorThread[] stressorThreads;
   private SizeThread sizeThread;
   private volatile boolean stressorsRunning = false;
   private int numSlaves;
   private int slaveIndex;
   private List<Stats> stats;
   private BackgroundStatsThread backgroundStatsThread;
   private long statsIteration;
   private boolean loaded = false;
   private int transactionSize;
   private List<Integer> loadDataForDeadSlaves;
   private String bucketId;

   public BackgroundStats(int puts, int gets, int removes, int numEntries, int entrySize, String bucketId, int numThreads, SlaveState slaveState,
         long delayBetweenRequests, int numSlaves, int slaveIndex, long statsIteration, int transactionSize,
         List<Integer> loadDataForDeadSlaves) {
      super();
      this.puts = puts;
      this.gets = gets;
      this.removes = removes;
      this.operations = puts + gets + removes;
      this.numEntries = numEntries;
      this.entrySize = entrySize;
      this.bucketId = bucketId;
      this.numThreads = numThreads;
      this.slaveState = slaveState;
      this.delayBetweenRequests = delayBetweenRequests;
      this.numSlaves = numSlaves;
      this.slaveIndex = slaveIndex;
      this.statsIteration = statsIteration;
      this.transactionSize = transactionSize;
      this.loadDataForDeadSlaves = loadDataForDeadSlaves;
   }

   /**
    * 
    * Starts numThreads stressors.
    * 
    */
   public synchronized void startStressors() {
      if (stressorThreads != null || stressorsRunning) {
         log.warn("Can't start stressors, they're already running.");
         return;
      }
      if (slaveState.getCacheWrapper() == null) {
         log.warn("Can't start stressors, cache wrapper not available");
         return;
      }
      stressorThreads = new StressorThread[numThreads];
      RangeHelper.Range slaveKeyRange = RangeHelper.divideRange(numEntries, numSlaves, slaveIndex);
      for (int i = 0; i < stressorThreads.length; i++) {
         RangeHelper.Range threadKeyRange = RangeHelper.divideRange(slaveKeyRange.getSize(), numThreads, i);
         stressorThreads[i] = new StressorThread(slaveKeyRange.getStart() + threadKeyRange.getStart(),
            slaveKeyRange.getStart() + threadKeyRange.getEnd(), i);
         stressorThreads[i].start();
      }
      sizeThread = new SizeThread();
      sizeThread.start();
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
         for (StressorThread st : stressorThreads) {
            if (!st.loaded) {
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
      sizeThread.interrupt();
      log.debug("Stopping stressors");
      for (int i = 0; i < stressorThreads.length; i++) {
         stressorThreads[i].terminate = true;
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
         sizeThread.join();
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
      if (backgroundStatsThread != null || stats != null) {
         log.info("Stats thread already running");
         return;
      }
      stats = new ArrayList<Stats>();
      backgroundStatsThread = new BackgroundStatsThread();
      backgroundStatsThread.start();
   }

   public synchronized List<Stats> stopStats() {
      if (backgroundStatsThread == null || stats == null) {
         throw new IllegalStateException("Stat thread not running");
      }
      backgroundStatsThread.interrupt();
      try {
         backgroundStatsThread.join();
      } catch (InterruptedException e) {
         log.error("Interrupted while waiting for stat thread to end.");
      }
      List<Stats> statsToReturn = stats;
      stats = null;
      return statsToReturn;
   }

   private class BackgroundStatsThread extends Thread {

      private Stats nodeDownStats = new Stats(false);

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

      private Stats gatherStats() {
         long now = System.currentTimeMillis();
         if (!stressorsRunning) {
            return nodeDownStats.snapshot(true, now);
         } else {
            nodeDownStats.reset(now); // we need to reset should we need them in next round
            Stats r = null;
            for (int i = 0; i < stressorThreads.length; i++) {
               Stats threadStats = stressorThreads[i].threadStats.snapshot(true, now);
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

   private class StressorThread extends Thread {

      private Random rand = new Random(); 
      private long lastOpStartTime;
      private Stats threadStats = new Stats();
      private int keyRangeStart;
      private int keyRangeEnd;
      private int currentKey;
      private volatile boolean terminate = false;
      private int remainingTxOps;
      private boolean loaded = BackgroundStats.this.loaded;
      private int idx;

      public StressorThread(int keyRangeStart, int keyRangeEnd, int idx) {
         super("StressorThread-" + idx);
         this.keyRangeStart = keyRangeStart;
         this.keyRangeEnd = keyRangeEnd;
         this.currentKey = keyRangeStart;
         this.remainingTxOps = transactionSize;
         this.idx = idx;
      }

      private void loadData() {
         log.trace("Loading key range [" + keyRangeStart + ", " + keyRangeEnd + "]");
         loadKeyRange(keyRangeStart, keyRangeEnd);            
         if (loadDataForDeadSlaves != null && !loadDataForDeadSlaves.isEmpty()) {
            // each slave takes responsibility to load keys for a subset of dead slaves
            RangeHelper.Range deadSlaveIdxRange = RangeHelper.divideRange(loadDataForDeadSlaves.size(), numSlaves - loadDataForDeadSlaves.size(), slaveIndex); // range of dead slaves this slave will load data for
            for (int deadSlaveIdx = deadSlaveIdxRange.getStart(); deadSlaveIdx < deadSlaveIdxRange.getEnd(); deadSlaveIdx++) {
               RangeHelper.Range deadSlaveKeyRange = RangeHelper.divideRange(numEntries, numSlaves, loadDataForDeadSlaves.get(deadSlaveIdx)); // key range for the current dead slave
               RangeHelper.Range keyRange = RangeHelper.divideRange(deadSlaveKeyRange.getSize(), numThreads, idx); // key range for this thread
               int deadKeyRangeStart = deadSlaveKeyRange.getStart() + keyRange.getStart();
               int deadKeyRangeEnd = deadSlaveKeyRange.getStart() + keyRange.getEnd();
               log.trace("Loading key range for dead slave " + loadDataForDeadSlaves.get(deadSlaveIdx) + ": ["
                     + deadKeyRangeStart + ", " + deadKeyRangeEnd + "]");
               loadKeyRange(deadKeyRangeStart, deadKeyRangeEnd);                  
            }
         }
         currentKey = keyRangeStart;
         loaded = true;
      }
      
      private void loadKeyRange(int from, int to) {         
         int loaded_keys = 0;
         CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
         for (currentKey = from; currentKey < to && !terminate; currentKey++, loaded_keys++) {
            try {
               cacheWrapper.put(bucketId, key(currentKey), generateRandomEntry(entrySize));
               if (loaded_keys % 1000 == 0) {
                  log.debug("Loaded " + loaded_keys + " out of " + (to - from));
               }
            } catch (Exception e) {
               log.error("Error while loading data", e);
            }
         }
         log.debug("Loaded all " + (to - from) + " keys");
      }

      @Override
      public void run() {
         try {
            if (!loaded) {
               loadData();
            }
            while (!isInterrupted() && !terminate) {
               makeRequest();
               sleep(delayBetweenRequests);
            }
         } catch (InterruptedException e) {
            log.trace("Stressor interrupted.");
            // we should close the transaction, otherwise TX Reaper would find dead thread in tx
            if (transactionSize != -1) {
               try {
                  CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
                  if (cacheWrapper != null && cacheWrapper.isRunning()) {
                     cacheWrapper.endTransaction(false);
                  }
               } catch (Exception e1) {
                  log.error("Error while ending transaction", e);
               }             
            }
         }
      }

      private String key(int key) {
         return "key" + key;
      }

      private void resetLastOpTime() {
         lastOpStartTime = System.currentTimeMillis();
      }

      private long lastOpTime() {
         return System.currentTimeMillis() - lastOpStartTime;
      }
      
      private Operation getOperation() {
         int r = rand.nextInt(operations);
         if (r < gets) {
            return Operation.GET;
         } else if (r < gets + puts) {
            return Operation.PUT;
         } else return Operation.REMOVE;
      }
            
      private void makeRequest() throws InterruptedException {
         String key = null;
         Operation operation = getOperation();
         CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
         try {
            key = key(currentKey++);
            if (currentKey == keyRangeEnd) {
               currentKey = keyRangeStart;
            }
            if (transactionSize != -1 && remainingTxOps == transactionSize) {
               cacheWrapper.startTransaction();
            }
            resetLastOpTime();
            Object result;
            switch (operation)
            {
            case GET:
               result = cacheWrapper.get(bucketId, key);
               threadStats.registerRequest(lastOpTime(), operation, result == null);
               break;
            case PUT:
               cacheWrapper.put(bucketId, key, generateRandomEntry(entrySize));
               threadStats.registerRequest(lastOpTime(), operation, false);
               break;
            case REMOVE:
               result = cacheWrapper.remove(bucketId, key);
               threadStats.registerRequest(lastOpTime(), operation, result == null);
               break;
            }
            if (transactionSize != -1) {
               remainingTxOps--;
               if (remainingTxOps == 0) {
                  cacheWrapper.endTransaction(true);
                  remainingTxOps = transactionSize;
               }
            }
         } catch (Exception e) {
            InterruptedException ie = findInterruptionCause(null, e);
            if (ie != null) {
               throw ie;
            } else if (e.getClass().getName().contains("SuspectException")) {
               log.error("Request failed due to SuspectException: " + e.getMessage());
            } else {
               log.error("Cache operation error", e);
            }
            if (transactionSize != -1) {
               try {
                  cacheWrapper.endTransaction(false);
               } catch (Exception e1) {
                  log.error("Error while ending transaction", e);
               }
               remainingTxOps = transactionSize;
            }
            threadStats.registerError(lastOpTime(), operation);
         }
      }

      private InterruptedException findInterruptionCause(Throwable eParent, Throwable e) {
         if (e == null || eParent == e) {
            return null;
         } else if (e instanceof InterruptedException) {
            return (InterruptedException) e;
         } else {
            return findInterruptionCause(e, e.getCause());
         }
      }

      private byte[] generateRandomEntry(int size) {
         // each char is 2 bytes         
         byte[] data = new byte[size];
         rand.nextBytes(data);
         return data;
      }
   }

   public static class Stats implements Serializable {

      protected boolean nodeUp = true;
      protected boolean snapshot = false;

      protected long requestsPut;
      protected long maxResponseTimePut = Long.MIN_VALUE;
      protected long responseTimeSumPut = 0;

      protected long requestsGet;
      protected long maxResponseTimeGet = Long.MIN_VALUE;
      protected long responseTimeSumGet = 0;
      protected long requestsNullGet;
      
      protected long requestsRemove;
      protected long maxResponseTimeRemove = Long.MIN_VALUE;
      protected long responseTimeSumRemove = 0;

      protected long intervalBeginTime;
      protected long intervalEndTime;
      protected long errorsPut = 0;
      protected long errorsGet = 0;
      protected long errorsRemove = 0;

      protected int cacheSize;

      public Stats(boolean nodeUp) {
         this.nodeUp = nodeUp;
      }

      public Stats() {
         super();
         intervalBeginTime = System.currentTimeMillis();
         intervalEndTime = intervalBeginTime;
      }

      public boolean isNodeUp() {
         return nodeUp;
      }

      public synchronized void registerRequest(long responseTime, Operation operation, boolean isNull) {
         ensureNotSnapshot();
         switch (operation)
         {
         case GET:
            requestsGet++;
            if (isNull) {
               requestsNullGet++;
            }
            responseTimeSumGet += responseTime;
            maxResponseTimeGet = Math.max(maxResponseTimeGet, responseTime);
            break;
         case PUT:
            requestsPut++;
            responseTimeSumPut += responseTime;
            maxResponseTimePut = Math.max(maxResponseTimePut, responseTime);
            break;
         case REMOVE:
            requestsRemove++;
            responseTimeSumRemove += responseTime;
            maxResponseTimeRemove = Math.max(maxResponseTimeRemove, responseTime);
            break;
         }

      }

      public synchronized void registerError(long responseTime, Operation operation) {
         switch (operation)
         {
         case GET:
            requestsGet++;
            errorsGet++;
            responseTimeSumGet += responseTime;
            maxResponseTimeGet = Math.max(maxResponseTimeGet, responseTime);
            break;
         case PUT:
            requestsPut++;
            errorsPut++;
            responseTimeSumPut += responseTime;
            maxResponseTimePut = Math.max(maxResponseTimePut, responseTime);
            break;
         case REMOVE:
            requestsRemove++;
            errorsRemove++;
            responseTimeSumRemove += responseTime;
            maxResponseTimeRemove = Math.max(maxResponseTimeRemove, responseTime);
            break;
         }
      }

      public synchronized void reset(long time) {
         ensureNotSnapshot();
         intervalBeginTime = time;
         intervalEndTime = intervalBeginTime;
         requestsPut = 0;
         requestsGet = 0;
         requestsNullGet = 0;
         requestsRemove = 0;
         responseTimeSumPut = 0;
         responseTimeSumGet = 0;
         responseTimeSumRemove = 0;
         maxResponseTimePut = Long.MIN_VALUE;
         maxResponseTimeGet = Long.MIN_VALUE;
         maxResponseTimeRemove = Long.MIN_VALUE;
         errorsPut = 0;
         errorsGet = 0;
         errorsRemove = 0;
      }

      public synchronized Stats snapshot(boolean reset, long time) {
         ensureNotSnapshot();
         Stats result = new Stats();
         
         fillCopy(result);
         
         result.intervalEndTime = time;
         result.snapshot = true;
         result.nodeUp = nodeUp;
         if (reset) {
            reset(time);
         }
         return result;
      }

      public synchronized Stats copy() {
         Stats result = new Stats();
         fillCopy(result);
         return result;
      }

      protected void fillCopy(Stats result) {
         result.snapshot = snapshot;

         result.intervalBeginTime = intervalBeginTime;
         result.intervalEndTime = intervalEndTime;

         result.requestsPut = requestsPut;
         result.requestsGet = requestsGet;
         result.requestsNullGet = requestsNullGet;
         result.requestsRemove = requestsRemove;
         result.maxResponseTimePut = maxResponseTimePut;
         result.maxResponseTimeGet = maxResponseTimeGet;
         result.maxResponseTimeRemove = maxResponseTimeRemove;

         result.responseTimeSumPut = responseTimeSumPut;
         result.responseTimeSumGet = responseTimeSumGet;
         result.responseTimeSumRemove = responseTimeSumRemove;
         result.errorsPut = errorsPut;
         result.errorsGet = errorsGet;
         result.errorsRemove = errorsRemove;
      }

      /**
       * 
       * Merge otherStats to this. leaves otherStats unchanged.
       * 
       * @param otherStats
       */
      public synchronized void merge(Stats otherStats) {
         ensureSnapshot();
         otherStats.ensureSnapshot();
         intervalBeginTime = Math.min(otherStats.intervalBeginTime, intervalBeginTime);
         intervalEndTime = Math.max(otherStats.intervalEndTime, intervalEndTime);
         requestsPut += otherStats.requestsPut;
         requestsGet += otherStats.requestsGet;         
         requestsNullGet += otherStats.requestsNullGet;
         requestsRemove += otherStats.requestsRemove;
         maxResponseTimePut = Math.max(otherStats.maxResponseTimePut, maxResponseTimePut);
         maxResponseTimeGet = Math.max(otherStats.maxResponseTimeGet, maxResponseTimeGet);
         maxResponseTimeRemove = Math.max(otherStats.maxResponseTimeRemove, maxResponseTimeRemove);
         errorsPut += otherStats.errorsPut;
         errorsGet += otherStats.errorsGet;
         errorsRemove += otherStats.errorsRemove;
         responseTimeSumGet += otherStats.responseTimeSumGet;
         responseTimeSumPut += otherStats.responseTimeSumPut;
         responseTimeSumRemove += otherStats.responseTimeSumRemove;
      }

      public synchronized static Stats merge(Collection<Stats> set) {
         if (set.size() == 0) {
            return null;
         }
         Iterator<Stats> elems = set.iterator();
         Stats res = elems.next().copy();
         while (elems.hasNext()) {
            res.merge(elems.next());
         }
         return res;
      }

      public boolean isSnapshot() {
         return snapshot;
      }

      protected void ensureSnapshot() {
         if (!snapshot) {
            throw new RuntimeException("this operation can be performed only on snapshot");
         }
      }

      protected void ensureNotSnapshot() {
         if (snapshot) {
            throw new RuntimeException("this operation cannot be performed on snapshot");
         }
      }

      public long getNumErrors() {
         return errorsPut + errorsGet + errorsRemove;
      }

      public long getNumErrorsPut() {
         return errorsPut;
      }

      public long getNumErrorsGet() {
         return errorsGet;
      }

      public long getSnapshotTime() {
         return intervalEndTime;
      }

      public long getMaxResponseTimePut() {
         return maxResponseTimePut;
      }

      public long getMaxResponseTimeGet() {
         return maxResponseTimeGet;
      }

      public synchronized long getNumberOfRequests() {
         return requestsPut + requestsGet + requestsRemove;
      }

      public synchronized long getMaxResponseTime() {
         return Math.max(maxResponseTimeGet, maxResponseTimePut);
      }

      public synchronized double getAvgResponseTimePut() {
         if (requestsPut == 0) {
            return Double.NaN;
         } else {
            return ((double) responseTimeSumPut) / ((double) requestsPut);
         }
      }

      public synchronized double getAvgResponseTimeGet() {
         if (requestsGet == 0) {
            return Double.NaN;
         } else {
            return ((double) responseTimeSumGet) / ((double) requestsGet);
         }
      }

      public synchronized double getAvgResponseTime() {
         if (getNumberOfRequests() == 0) {
            return Double.NaN;
         } else {
            return ((double) responseTimeSumPut + responseTimeSumGet + responseTimeSumRemove) / ((double) getNumberOfRequests());
         }
      }

      public synchronized long getDuration() {
         return intervalEndTime - intervalBeginTime;
      }

      public synchronized long getIntervalBeginTime() {
         return intervalBeginTime;
      }

      public synchronized long getIntervalEndTime() {
         return intervalEndTime;
      }

      public synchronized double getThroughputPut() {
         if (getDuration() == 0) {
            return Double.NaN;
         } else {
            return ((double) requestsPut) * ((double) 1000) / ((double) getDuration());
         }
      }

      public synchronized double getThroughputGet() {
         if (getDuration() == 0) {
            return Double.NaN;
         } else {
            return ((double) requestsGet) * ((double) 1000) / ((double) getDuration());
         }
      }

      public synchronized double getThroughput() {
         if (getDuration() == 0) {
            return Double.NaN;
         } else {
            return ((double) getNumberOfRequests()) * ((double) 1000) / ((double) getDuration());
         }
      }

      @Override
      public String toString() {
         return "Stats(reqs=" + getNumberOfRequests() + ")";
      }

      public int getCacheSize() {
         return cacheSize;
      }

      public static double getCacheSizeMaxRelativeDeviation(List<Stats> stats) {
         if (stats.isEmpty() || stats.size() == 1) {
            return 0;
         }
         double sum = 0;
         int cnt = 0;
         for (Stats s : stats) {
            if (s.isNodeUp() && s.cacheSize != -1) {
               sum += (double) s.cacheSize;
               cnt++;
            }
         }
         double avg = sum / ((double) cnt);
         double maxDev = -1;
         for (Stats s : stats) {
            if (s.isNodeUp() && s.cacheSize != -1) {
               double dev = Math.abs(avg - ((double) s.cacheSize));
               if (dev > maxDev) {
                  maxDev = dev;
               }
            }
         }
         return (maxDev / avg) * 100d;
      }

      public long getRequestsNullGet() {
         return requestsNullGet;
      }

      public static long getIntervalBeginMin(List<Stats> stats) {
         long ret = Long.MAX_VALUE;
         for (Stats s : stats) {
            if (s.intervalBeginTime < ret) {
               ret = s.intervalBeginTime;
            }
         }
         return ret;
      }

      public static long getIntervalBeginMax(List<Stats> stats) {
         long ret = Long.MIN_VALUE;
         for (Stats s : stats) {
            if (s.intervalBeginTime > ret) {
               ret = s.intervalBeginTime;
            }
         }
         return ret;
      }

      public static long getIntervalEndMin(List<Stats> stats) {
         long ret = Long.MAX_VALUE;
         for (Stats s : stats) {
            if (s.intervalEndTime < ret) {
               ret = s.intervalEndTime;
            }
         }
         return ret;
      }

      public static long getIntervalEndMax(List<Stats> stats) {
         long ret = Long.MIN_VALUE;
         for (Stats s : stats) {
            if (s.intervalEndTime > ret) {
               ret = s.intervalEndTime;
            }
         }
         return ret;
      }

      public static double getTotalThroughput(List<Stats> stats) {
         double ret = 0;
         for (Stats s : stats) {
            ret += s.getThroughput();
         }
         return ret;
      }

      public static double getAvgThroughput(List<Stats> stats) {
         return getTotalThroughput(stats) / ((double) stats.size());
      }

      public static double getAvgRespTime(List<Stats> stats) {
         long responseTimeSum = 0;
         long numRequests = 0;
         for (Stats s : stats) {
            responseTimeSum += s.responseTimeSumGet;
            responseTimeSum += s.responseTimeSumPut;
            responseTimeSum += s.responseTimeSumRemove;
            numRequests += s.requestsGet;
            numRequests += s.requestsPut;
            numRequests += s.requestsRemove;
         }
         return ((double) responseTimeSum) / ((double) numRequests);
      }

      public static long getMaxRespTime(List<Stats> stats) {
         long ret = Long.MIN_VALUE;
         for (Stats s : stats) {
            ret = Math.max(ret, s.maxResponseTimeGet);
            ret = Math.max(ret, s.maxResponseTimePut);
            ret = Math.max(ret, s.maxResponseTimeRemove);            
         }
         return ret;
      }

      public static long getTotalCacheSize(List<Stats> stats) {
         long ret = 0;
         for (Stats s : stats) {
            ret += s.getCacheSize();
         }
         return ret;
      }

      public static long getTotalErrors(List<Stats> stats) {
         long ret = 0;
         for (Stats s : stats) {
            ret += s.getNumErrors();
         }
         return ret;
      }

      public static long getTotalNullRequests(List<Stats> stats) {
         long ret = 0;
         for (Stats s : stats) {
            ret += s.getRequestsNullGet();
         }
         return ret;
      }
   }

   public static void beforeCacheWrapperDestroy(SlaveState slaveState) {
      BackgroundStats bgStats = (BackgroundStats) slaveState.get(BackgroundStats.NAME);
      if (bgStats != null) {
         bgStats.stopStressors();
      }
   }

   public static void afterCacheWrapperStart(SlaveState slaveState) {
      BackgroundStats bgStats = (BackgroundStats) slaveState.get(BackgroundStats.NAME);
      if (bgStats != null) {
         bgStats.setLoaded(); // don't load data at this stage
         bgStats.startStressors();
      }
   }

   public void setLoaded() {
      loaded = true;
   }
   
   public String getBucketId() {
      return bucketId;
   }
}
