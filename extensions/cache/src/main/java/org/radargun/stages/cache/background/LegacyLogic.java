package org.radargun.stages.cache.background;

import java.util.List;
import java.util.Random;

import org.radargun.Operation;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.Range;
import org.radargun.stats.Request;
import org.radargun.stats.RequestSet;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Transactional;
import org.radargun.utils.Utils;

/**
 * Original background stressors logic which loads all entries into cache and then overwrites them.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
class LegacyLogic extends AbstractLogic {
   // these two caches can be transactional internally (with autocommit)
   // but must not be used between begin() and commit() | rollback()
   private final BasicOperations.Cache nonTxBasicCache;
   private final ConditionalOperations.Cache nonTxConditionalCache;
   private final long keyRangeStart;
   private final long keyRangeEnd;
   private final List<Range> deadSlavesRanges;
   private final boolean loadOnly;
   private final boolean putWithReplace;
   private final Random rand = new Random();
   private BasicOperations.Cache basicCache;
   private ConditionalOperations.Cache conditionalCache;
   private volatile long currentKey;
   private int remainingTxOps;
   private boolean loaded;
   private RequestSet transactionalRequests;
   private ValueGenerator valueGenerator;

   LegacyLogic(BackgroundOpsManager manager, Range range, List<Range> deadSlavesRanges, boolean loaded) {
      super(manager);
      this.manager = manager;
      this.nonTxBasicCache = manager.getBasicCache();
      this.nonTxConditionalCache = manager.getConditionalCache();
      if (transactionSize <= 0) {
         basicCache = nonTxBasicCache;
         conditionalCache = nonTxConditionalCache;
      }
      this.keyRangeStart = range.getStart();
      this.keyRangeEnd = range.getEnd();
      this.deadSlavesRanges = deadSlavesRanges;
      this.loadOnly = manager.getLegacyLogicConfiguration().isLoadOnly();
      this.putWithReplace = manager.getLegacyLogicConfiguration().isPutWithReplace();
      this.valueGenerator = manager.getLegacyLogicConfiguration().getValueGenerator();
      this.loaded = loaded;
      this.currentKey = range.getStart();
      this.remainingTxOps = transactionSize;
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

   private void loadKeyRange(long from, long to) {
      int loadedKeys = 0;
      boolean loadWithPutIfAbsent = manager.getLegacyLogicConfiguration().isLoadWithPutIfAbsent();
      int entrySize = manager.getLegacyLogicConfiguration().getEntrySize();
      for (long keyId = from; keyId < to && !stressor.isTerminated(); keyId++, loadedKeys++) {
         while (!stressor.isTerminated()) {
            try {
               Object key = keyGenerator.generateKey(keyId);
               Object value = valueGenerator.generateValue(keyId, entrySize, rand);
               if (loadWithPutIfAbsent) {
                  nonTxConditionalCache.putIfAbsent(key, value);
               } else {
                  nonTxBasicCache.put(key, value);
               }
               if (loadedKeys % 1000 == 0) {
                  log.debug("Loaded " + loadedKeys + " out of " + (to - from));
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

   @Override
   public void init() {
      // TODO: maybe loadData should go here?
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
      Operation operation = getOperation(rand);
      try {
         Object key = keyGenerator.generateKey(currentKey++);
         if (currentKey == keyRangeEnd) {
            currentKey = keyRangeStart;
         }
         if (transactionSize > 0 && remainingTxOps == transactionSize) {
            ongoingTx = manager.newTransaction();
            basicCache = ongoingTx.wrap(nonTxBasicCache);
            conditionalCache = ongoingTx.wrap(nonTxConditionalCache);
            Request beginRequest = stressor.stats.startRequest();
            beginRequest.exec(Transactional.BEGIN, () -> ongoingTx.begin());
            transactionalRequests = stressor.stats.requestSet();
            transactionalRequests.add(beginRequest);
         }
         Request request = stressor.stats.startRequest();
         try {
            Object result;
            if (operation == BasicOperations.GET) {
               result = basicCache.get(key);
               if (result == null) operation = GET_NULL;
            } else if (operation == BasicOperations.PUT) {
               int entrySize = manager.getLegacyLogicConfiguration().getEntrySize();
               Object value = valueGenerator.generateValue(key, entrySize, rand);
               if (putWithReplace) {
                  conditionalCache.replace(key, value);
               } else {
                  basicCache.put(key, value);
               }
            } else if (operation == BasicOperations.REMOVE) {
               basicCache.remove(key);
            } else {
               throw new IllegalArgumentException();
            }
            request.succeeded(operation);
         } catch (Exception e) {
            request.failed(operation);
            throw e;
         } finally {
            if (transactionalRequests != null) {
               transactionalRequests.add(request);
            }
         }
         if (transactionSize > 0) {
            remainingTxOps--;
            if (remainingTxOps == 0) {
               Request commitRequest = stressor.stats.startRequest();
               try {
                  ongoingTx.commit();
                  commitRequest.succeeded(Transactional.COMMIT);
               } catch (Exception e) {
                  commitRequest.succeeded(Transactional.COMMIT);
                  throw e;
               } finally {
                  if (transactionalRequests != null) {
                     transactionalRequests.add(commitRequest);
                     transactionalRequests.finished(commitRequest.isSuccessful(), Transactional.DURATION);
                  }
                  txCleanup();
               }
               remainingTxOps = transactionSize;
            }
         }
      } catch (Exception e) {
         InterruptedException ie = Utils.findThrowableCauseByClass(e, InterruptedException.class);
         if (ie != null) {
            throw ie;
         } else if (e.getClass().getName().contains("SuspectException")) {
            log.error("Request failed due to SuspectException: " + e.getMessage());
         } else {
            log.error("Cache operation error", e);
         }
         if (transactionSize > 0) {
            try {
               ongoingTx.rollback();
            } catch (Exception e1) {
               log.error("Error while ending transaction", e);
            } finally {
               txCleanup();
            }
            remainingTxOps = transactionSize;
         }
      }
   }

   private void txCleanup() {
      ongoingTx = null;
      basicCache = null;
      conditionalCache = null;
   }

   @Override
   public String getStatus() {
      return String.format("currentKey=%s, remainingTxOps=%d", keyGenerator.generateKey(currentKey), remainingTxOps);
   }

   public boolean isLoaded() {
      return loaded;
   }
}
