package org.radargun.stressors;

import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.radargun.CacheWrapper;
import org.radargun.stages.helpers.Range;
import org.radargun.state.SlaveState;

/**
* Stressor thread running during many stages.
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
* @since 1/3/13
*/
class BackgroundStressor extends Thread {

   private static Logger log = Logger.getLogger(BackgroundStressor.class);

   private Random rand = new Random();
   private long lastOpStartTime;
   private SynchronizedStatistics threadStats = new SynchronizedStatistics();
   private int keyRangeStart;
   private int keyRangeEnd;
   private List<Range> deadSlavesRanges;
   private int currentKey;
   private volatile boolean terminate = false;
   private int remainingTxOps;
   private boolean loaded;
   private BackgroundOpsManager backgroundOpsManager;
   private KeyGenerator keyGenerator;

   public BackgroundStressor(BackgroundOpsManager backgroundOpsManager, SlaveState slaveState, Range myRange, List<Range> deadSlavesRanges, int idx) {
      super("StressorThread-" + idx);
      this.keyRangeStart = myRange.getStart();
      this.keyRangeEnd = myRange.getEnd();
      this.deadSlavesRanges = deadSlavesRanges;
      this.currentKey = myRange.getStart();
      this.remainingTxOps = backgroundOpsManager.getTransactionSize();
      this.backgroundOpsManager = backgroundOpsManager;
      this.keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
      if (this.keyGenerator == null) {
         this.keyGenerator = new StringKeyGenerator();
         slaveState.put(KeyGenerator.KEY_GENERATOR, this.keyGenerator);
      }
   }

   private void loadData() {
      log.trace("Loading key range [" + keyRangeStart + ", " + keyRangeEnd + "]");
      loadKeyRange(keyRangeStart, keyRangeEnd);
      if (deadSlavesRanges != null) {
         for (Range range : deadSlavesRanges) {
            log.trace("Loading key range for dead slave: [" + range.getStart() + ", " + range.getEnd() + "]");
            loadKeyRange(range.getStart(), range.getEnd());
         }
      }
      currentKey = keyRangeStart;
      loaded = true;
   }

   private void loadKeyRange(int from, int to) {
      int loaded_keys = 0;
      CacheWrapper cacheWrapper = backgroundOpsManager.getCacheWrapper();
      for (currentKey = from; currentKey < to && !terminate; currentKey++, loaded_keys++) {
         try {
            cacheWrapper.put(backgroundOpsManager.getBucketId(), keyGenerator.generateKey(currentKey), generateRandomEntry(backgroundOpsManager.getEntrySize()));
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
            sleep(backgroundOpsManager.getDelayBetweenRequests());
         }
      } catch (InterruptedException e) {
         log.trace("Stressor interrupted.");
         // we should close the transaction, otherwise TX Reaper would find dead thread in tx
         if (backgroundOpsManager.getTransactionSize() != -1) {
            try {
               CacheWrapper cacheWrapper = backgroundOpsManager.getCacheWrapper();
               if (cacheWrapper != null && cacheWrapper.isRunning()) {
                  cacheWrapper.endTransaction(false);
               }
            } catch (Exception e1) {
               log.error("Error while ending transaction", e);
            }
         }
      }
   }

   private void resetLastOpTime() {
      lastOpStartTime = System.nanoTime();
   }

   private long lastOpTime() {
      return System.nanoTime() - lastOpStartTime;
   }

   private void makeRequest() throws InterruptedException {
      Object key = null;
      Operation operation = backgroundOpsManager.getOperation(rand);
      CacheWrapper cacheWrapper = backgroundOpsManager.getCacheWrapper();
      try {
         key = keyGenerator.generateKey(currentKey++);
         if (currentKey == keyRangeEnd) {
            currentKey = keyRangeStart;
         }
         int transactionSize = backgroundOpsManager.getTransactionSize();
         if (transactionSize != -1 && remainingTxOps == transactionSize) {
            cacheWrapper.startTransaction();
         }
         resetLastOpTime();
         Object result;
         switch (operation)
         {
         case GET:
            result = cacheWrapper.get(backgroundOpsManager.getBucketId(), key);
            if (result == null) operation = Operation.GET_NULL;
            threadStats.registerRequest(lastOpTime(), 0, operation);
            break;
         case PUT:
            cacheWrapper.put(backgroundOpsManager.getBucketId(), key, generateRandomEntry(backgroundOpsManager.getEntrySize()));
            threadStats.registerRequest(lastOpTime(), 0, operation);
            break;
         case REMOVE:
            cacheWrapper.remove(backgroundOpsManager.getBucketId(), key);
            threadStats.registerRequest(lastOpTime(), 0, operation);
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
         if (backgroundOpsManager.getTransactionSize() != -1) {
            try {
               cacheWrapper.endTransaction(false);
            } catch (Exception e1) {
               log.error("Error while ending transaction", e);
            }
            remainingTxOps = backgroundOpsManager.getTransactionSize();
         }
         threadStats.registerError(lastOpTime(), 0, operation);
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

   public boolean isLoaded() {
      return loaded;
   }

   public void requestTerminate() {
      terminate = true;
   }

   public SynchronizedStatistics getStatsSnapshot(boolean reset, long time) {
      SynchronizedStatistics snapshot = threadStats.snapshot(reset,  time);
      return snapshot;
   }

   public void setLoaded(boolean loaded) {
      this.loaded = loaded;
   }
}
