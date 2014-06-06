package org.radargun.stages.cache.background;

import java.util.List;
import java.util.Random;

import org.radargun.Operation;
import org.radargun.stages.helpers.Range;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Transactional;

/**
 * Original background stressors logic which loads all entries into cache and then overwrites them.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
class LegacyLogic extends AbstractLogic {
   private final BasicOperations.Cache basicCache;
   private final ConditionalOperations.Cache conditionalCache;
   private final int keyRangeStart;
   private final int keyRangeEnd;
   private final List<Range> deadSlavesRanges;
   private final boolean loadOnly;
   private final boolean putWithReplace;
   private final Random rand = new Random();
   private volatile long currentKey;
   private int remainingTxOps;
   private boolean loaded;
   private long transactionStart;



   LegacyLogic(BackgroundOpsManager manager, Range range, List<Range> deadSlavesRanges, boolean loaded) {
      super(manager);
      this.manager = manager;
      this.basicCache = manager.getBasicCache();
      this.conditionalCache = manager.getConditionalCache();
      this.keyRangeStart = range.getStart();
      this.keyRangeEnd = range.getEnd();
      this.deadSlavesRanges = deadSlavesRanges;
      this.loadOnly = manager.getLegacyLogicConfiguration().isLoadOnly();
      this.putWithReplace = manager.getLegacyLogicConfiguration().isPutWithReplace();
      this.loaded = loaded;
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
      boolean loadWithPutIfAbsent = manager.getLegacyLogicConfiguration().isLoadWithPutIfAbsent();
      int entrySize = manager.getLegacyLogicConfiguration().getEntrySize();
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
      if (!loaded) {
         loadData();
         loaded = true;
      }
      if (loadOnly) {
         log.info("Data have been loaded, terminating.");
         return;
      }
      long startTime = 0;
      Object key = null;
      Operation operation = manager.getOperation(rand);
      try {
         key = keyGenerator.generateKey(currentKey++);
         if (currentKey == keyRangeEnd) {
            currentKey = keyRangeStart;
         }
         if (transactionSize > 0 && remainingTxOps == transactionSize) {
            try {
               transactionStart = System.nanoTime();
               txCache.startTransaction();
               stressor.stats.registerRequest(System.nanoTime() - transactionStart, Transactional.BEGIN);
            } catch (Exception e) {
               stressor.stats.registerError(System.nanoTime() - transactionStart, Transactional.BEGIN);
               throw e;
            }
         }
         startTime = System.nanoTime();
         Object result;
         if (operation == BasicOperations.GET) {
            result = basicCache.get(key);
            if (result == null) operation = BasicOperations.GET_NULL;
         } else if (operation == BasicOperations.PUT) {
            if (putWithReplace) {
               conditionalCache.replace(key, generateRandomEntry(rand, manager.getLegacyLogicConfiguration().getEntrySize()));
            } else {
               basicCache.put(key, generateRandomEntry(rand, manager.getLegacyLogicConfiguration().getEntrySize()));
            }
         } else if (operation == BasicOperations.REMOVE) {
            basicCache.remove(key);
         } else {
            throw new IllegalArgumentException();
         }
         stressor.stats.registerRequest(System.nanoTime() - startTime, operation);
         if (transactionSize > 0) {
            remainingTxOps--;
            if (remainingTxOps == 0) {
               long commitStart = System.nanoTime();
               try {
                  txCache.endTransaction(true);
                  long commitEnd = System.nanoTime();
                  stressor.stats.registerRequest(commitEnd - commitStart, Transactional.COMMIT);
                  stressor.stats.registerRequest(commitEnd - transactionStart, Transactional.DURATION);
               } catch (Exception e) {
                  long commitEnd = System.nanoTime();
                  stressor.stats.registerError(commitEnd - commitStart, Transactional.COMMIT);
                  stressor.stats.registerError(commitEnd - transactionStart, Transactional.DURATION);
                  throw e;
               }
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
         if (transactionSize > 0) {
            try {
               txCache.endTransaction(false);
            } catch (Exception e1) {
               log.error("Error while ending transaction", e);
            }
            remainingTxOps = transactionSize;
         }
         stressor.stats.registerError(startTime <= 0 ? 0 : System.nanoTime() - startTime, operation);
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

   public boolean isLoaded() {
      return loaded;
   }
}
