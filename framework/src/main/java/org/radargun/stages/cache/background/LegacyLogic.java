package org.radargun.stages.cache.background;

import java.util.List;
import java.util.Random;

import org.radargun.stages.helpers.Range;
import org.radargun.stats.Operation;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
class LegacyLogic extends AbstractLogic {
   private final BasicOperations.Cache basicCache;
   private final ConditionalOperations.Cache conditionalCache;
   private final int keyRangeStart;
   private final int keyRangeEnd;
   private final List<Range> deadSlavesRanges;
   private final Random rand = new Random();
   private volatile long currentKey;
   private int remainingTxOps;

   LegacyLogic(BackgroundOpsManager manager, Range range, List<Range> deadSlavesRanges) {
      super(manager);
      this.manager = manager;
      this.basicCache = manager.getBasicCache();
      this.conditionalCache = manager.getConditionalCache();
      this.keyRangeStart = range.getStart();
      this.keyRangeEnd = range.getEnd();
      this.deadSlavesRanges = deadSlavesRanges;
      currentKey = range.getStart();
      remainingTxOps = transactionSize;
   }

   public void loadData() {
      log.trace("Loading key range [" + keyRangeStart + ", " + keyRangeEnd + "]");
      loadKeyRange(keyRangeStart, keyRangeEnd);
      if (deadSlavesRanges != null) {
         for (Range range : deadSlavesRanges) {
            log.trace("Loading key range for dead slave: [" + range.getStart() + ", " + range.getEnd() + "]");
            loadKeyRange(range.getStart(), range.getEnd());
         }
      }
   }

   private void loadKeyRange(int from, int to) {
      int loaded_keys = 0;
      boolean loadWithPutIfAbsent = manager.getLoadWithPutIfAbsent();
      int entrySize = manager.getEntrySize();
      Random rand = new Random();
      for (long keyId = from; keyId < to && !stressor.isTerminated(); keyId++, loaded_keys++) {
         while (!stressor.isTerminated()) {
            try {
               Object key = keyGenerator.generateKey(keyId);
               if (loadWithPutIfAbsent) {
                  conditionalCache.putIfAbsent(key, generateRandomEntry(rand, entrySize));
               } else {
                  basicCache.put(key, generateRandomEntry(rand, entrySize));
               }
               if (loaded_keys % 1000 == 0) {
                  log.debug("Loaded " + loaded_keys + " out of " + (to - from));
               }
               // if we get an exception, it's OK - we can retry.
               break;
            } catch (Exception e) {
               log.error("Error while loading data", e);
            }
         }
      }
      log.debug("Loaded all " + (to - from) + " keys");
   }

   public void invoke() throws InterruptedException {
      long startTime = 0;
      Object key = null;
      Operation operation = manager.getOperation(rand);
      try {
         key = keyGenerator.generateKey(currentKey++);
         if (currentKey == keyRangeEnd) {
            currentKey = keyRangeStart;
         }
         int transactionSize = manager.getTransactionSize();
         if (transactionSize > 0 && remainingTxOps == transactionSize) {
            txCache.startTransaction();
         }
         startTime = System.nanoTime();
         Object result;
         switch (operation)
         {
         case GET:
            result = basicCache.get(key);
            if (result == null) operation = Operation.GET_NULL;
            break;
         case PUT:
            basicCache.put(key, generateRandomEntry(rand, manager.getEntrySize()));
            break;
         case REMOVE:
            basicCache.remove(key);
            break;
         }
         stressor.stats.registerRequest(System.nanoTime() - startTime, 0, operation);
         if (transactionSize > 0) {
            remainingTxOps--;
            if (remainingTxOps == 0) {
               txCache.endTransaction(true);
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
         if (manager.getTransactionSize() > 0) {
            try {
               txCache.endTransaction(false);
            } catch (Exception e1) {
               log.error("Error while ending transaction", e);
            }
            remainingTxOps = manager.getTransactionSize();
         }
         stressor.stats.registerError(startTime <= 0 ? 0 : System.nanoTime() - startTime, 0, operation);
      }
   }

   private byte[] generateRandomEntry(Random rand, int size) {
      // each char is 2 bytes
      byte[] data = new byte[size];
      rand.nextBytes(data);
      return data;
   }

   @Override
   public String getStatus() {
      return String.format("currentKey=%s, remainingTxOps=%d", keyGenerator.generateKey(currentKey), remainingTxOps);
   }
}
