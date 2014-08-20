package org.radargun.stages.cache.stresstest;

import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Statistics;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Queryable;
import org.radargun.traits.Transactional;

/**
 * Each stressor operates according to its {@link OperationLogic logic} - the instance is private to each thread.
 * After finishing the {@linkplain OperationLogic#init(Stressor) init phase}, all stressors synchronously
 * execute logic's {@link OperationLogic#run(Stressor) run} method until
 * the {@link Completion#moreToRun(int)} returns false.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class Stressor extends Thread {
   private static Log log = LogFactory.getLog(Stressor.class);

   private int threadIndex;
   private int nodeIndex;
   private int numNodes;
   private int txRemainingOperations = 0;
   private long transactionDuration = 0;
   private Statistics stats;
   private OperationLogic logic;
   private boolean useTransactions;
   private StressTestStage stage;
   private PhaseSynchronizer synchronizer;
   private Completion completion;
   private Random random;

   private final BasicOperations.Cache nonTxBasicCache;
   private final ConditionalOperations.Cache nonTxConditionalCache;
   private final BulkOperations.Cache nonTxBulkNativeCache;
   private final BulkOperations.Cache nonTxBulkAsyncCache;
   private BasicOperations.Cache basicCache;
   private ConditionalOperations.Cache conditionalCache;
   private BulkOperations.Cache bulkNativeCache;
   private BulkOperations.Cache bulkAsyncCache;
   private Queryable queryable;

   private Transactional.Transaction ongoingTx;

   public Stressor(StressTestStage stage, OperationLogic logic, int threadIndex, int nodeIndex, int numNodes) {
      super("Stressor-" + threadIndex);
      this.stage = stage;
      this.threadIndex = threadIndex;
      this.nodeIndex = nodeIndex;
      this.numNodes = numNodes;
      this.logic = logic;
      synchronizer = stage.getSynchronizer();
      completion = stage.getCompletion();
      if (stage.getSeed() != null) {
         random = new Random(stage.getSeed() + threadIndex + nodeIndex * stage.numThreads);
      } else {
         random = new Random();
      }

      String cacheName = stage.bucketPolicy.getBucketName(threadIndex);
      nonTxBasicCache = stage.basicOperations == null ? null : stage.basicOperations.getCache(cacheName);
      nonTxConditionalCache = stage.conditionalOperations == null ? null : stage.conditionalOperations.getCache(cacheName);
      if (stage.bulkOperations != null) {
         nonTxBulkNativeCache = stage.bulkOperations.getCache(cacheName, false);
         nonTxBulkAsyncCache = stage.bulkOperations.getCache(cacheName, true);
      } else {
         nonTxBulkAsyncCache = nonTxBulkNativeCache = null;
      }
      // default for Transactional.Configuration.TRANSACTIONS_ENABLED is without transactions
      useTransactions = stage.useTransactions != null ? stage.useTransactions :
            stage.transactional == null ? false : stage.transactional.getConfiguration(cacheName) == Transactional.Configuration.TRANSACTIONAL;
      if (!useTransactions) {
         basicCache = nonTxBasicCache;
         conditionalCache = nonTxConditionalCache;
         bulkNativeCache = nonTxBulkNativeCache;
         bulkAsyncCache = nonTxBulkAsyncCache;
      }
   }

   public void setQueryable(Queryable queryable) {
      this.queryable = queryable;
   }

   @Override
   public void run() {
      try {
         for (;;) {
            synchronizer.slavePhaseStart();
            if (stage.isFinished()) {
               synchronizer.slavePhaseEnd();
               break;
            }
            try {
               if (!stage.isTerminated()) {
                  logic.init(this);
               }
               stats = stage.createStatistics();
            } catch (RuntimeException e) {
               stage.setTerminated();
               log.error("Unexpected error in stressor!", e);
            } finally {
               synchronizer.slavePhaseEnd();
            }
            synchronizer.slavePhaseStart();
            try {
               if (!stage.isTerminated()) {
                  log.trace("Starting thread: " + getName());
                  stats.begin();
                  runInternal();
               }
            } catch (Exception e) {
               stage.setTerminated();
               log.error("Unexpected error in stressor!", e);
            } finally {
               if (stats != null) {
                  stats.end();
               }
               synchronizer.slavePhaseEnd();
            }
         }
      } catch (Exception e) {
         log.error("Unexpected error in stressor!", e);
      }
   }

   private void runInternal() {
      int i = 0;
      while (completion.moreToRun(i)) {
         Object result = null;
         try {
            result = logic.run(this);
         } catch (OperationLogic.RequestException e) {
            // the exception was already logged in makeRequest
         }
         i++;
         completion.logProgress(i);
         if (i % stage.logPeriod == 0) StressTestStage.avoidJit(result);
      }

      if (txRemainingOperations > 0) {
         try {
            long endTxTime = endTransaction();
            stats.registerRequest(endTxTime, stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerRequest(transactionDuration + endTxTime, Transactional.DURATION);
         } catch (TransactionException e) {
            stats.registerError(e.getOperationDuration(), stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerError(transactionDuration + e.getOperationDuration(), Transactional.DURATION);
         } finally {
            clearTransaction();
         }
         transactionDuration = 0;
      }
   }

   public Object makeRequest(Operation operation, Object... keysAndValues) throws OperationLogic.RequestException {
      long startTxTime = 0;
      if (useTransactions && txRemainingOperations <= 0) {
         try {
            ongoingTx = stage.transactional.getTransaction();
            basicCache = ongoingTx.wrap(nonTxBasicCache);
            conditionalCache = ongoingTx.wrap(nonTxConditionalCache);
            bulkNativeCache = ongoingTx.wrap(nonTxBulkNativeCache);
            bulkAsyncCache = ongoingTx.wrap(nonTxBulkAsyncCache);

            startTxTime = startTransaction();
            transactionDuration = startTxTime;
            txRemainingOperations = stage.transactionSize;
            stats.registerRequest(startTxTime, Transactional.BEGIN);
         } catch (TransactionException e) {
            stats.registerError(e.getOperationDuration(), Transactional.BEGIN);
            return null;
         }
      }

      Object result = null;
      boolean successful = true;
      Exception exception = null;
      long start = System.nanoTime();
      long operationDuration;
      try {
         if (operation == BasicOperations.GET || operation == BasicOperations.GET_NULL) {
            result = basicCache.get(keysAndValues[0]);
            operation = (result != null ? BasicOperations.GET : BasicOperations.GET_NULL);
         } else if (operation == BasicOperations.PUT) {
            basicCache.put(keysAndValues[0], keysAndValues[1]);
         } else if (operation == Queryable.QUERY) {
            result = ((Queryable.Query) keysAndValues[0]).execute();
         } else if (operation == BasicOperations.REMOVE) {
            result = basicCache.remove(keysAndValues[0]);
         } else if (operation == ConditionalOperations.REMOVE_EXEC) {
            successful = conditionalCache.remove(keysAndValues[0], keysAndValues[1]);
         } else if (operation == ConditionalOperations.REMOVE_NOTEX) {
            successful = !conditionalCache.remove(keysAndValues[0], keysAndValues[1]);
         } else if (operation == ConditionalOperations.PUT_IF_ABSENT_EXEC) {
            result = conditionalCache.putIfAbsent(keysAndValues[0], keysAndValues[1]);
            successful = result == null;
         } else if (operation == ConditionalOperations.PUT_IF_ABSENT_NOTEX) {
            result = conditionalCache.putIfAbsent(keysAndValues[0], keysAndValues[1]);
            successful = keysAndValues[2].equals(result);
         } else if (operation == ConditionalOperations.REPLACE_EXEC) {
            successful = conditionalCache.replace(keysAndValues[0], keysAndValues[1], keysAndValues[2]);
         } else if (operation == ConditionalOperations.REPLACE_NOTEX) {
            successful = !conditionalCache.replace(keysAndValues[0], keysAndValues[1], keysAndValues[2]);
         } else if (operation == BulkOperations.GET_ALL_NATIVE) {
            result = bulkNativeCache.getAll((Set<Object>) keysAndValues[0]);
         } else if (operation == BulkOperations.GET_ALL_ASYNC) {
            result = bulkAsyncCache.getAll((Set<Object>) keysAndValues[0]);
         } else if (operation == BulkOperations.PUT_ALL_NATIVE) {
            bulkNativeCache.putAll((Map<Object, Object>) keysAndValues[0]);
         } else if (operation == BulkOperations.PUT_ALL_ASYNC) {
            bulkAsyncCache.putAll((Map<Object, Object>) keysAndValues[0]);
         } else if (operation == BulkOperations.REMOVE_ALL_NATIVE) {
            bulkNativeCache.removeAll((Set<Object>) keysAndValues[0]);
         } else if (operation == BulkOperations.REMOVE_ALL_ASYNC) {
            bulkAsyncCache.removeAll((Set<Object>) keysAndValues[0]);
         } else {
            throw new IllegalArgumentException();
         }
         operationDuration = System.nanoTime() - start;
         txRemainingOperations--;
      } catch (Exception e) {
         operationDuration = System.nanoTime() - start;
         log.warn("Error in request", e);
         successful = false;
         txRemainingOperations = 0;
         exception = e;
      }
      transactionDuration += operationDuration;

      long endTxTime = 0;
      if (useTransactions && txRemainingOperations <= 0) {
         try {
            endTxTime = endTransaction();
            stats.registerRequest(endTxTime, stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerRequest(transactionDuration + endTxTime, Transactional.DURATION);
         } catch (TransactionException e) {
            endTxTime = e.getOperationDuration();
            stats.registerError(endTxTime, stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerError(transactionDuration + endTxTime, Transactional.DURATION);
         } finally {
            clearTransaction();
         }
      }
      if (successful) {
         stats.registerRequest(operationDuration, operation);
         if (useTransactions && stage.transactionSize == 1) {
            stats.registerRequest(startTxTime + operationDuration + endTxTime, operation.derive("TX"));
         }
      } else {
         stats.registerError(operationDuration, operation);
         if (useTransactions && stage.transactionSize == 1) {
            stats.registerError(startTxTime + operationDuration + endTxTime, operation.derive("TX"));
         }
      }
      if (exception != null) {
         throw new OperationLogic.RequestException(exception);
      }
      return result;
   }

   private void clearTransaction() {
      ongoingTx = null;
      basicCache = null;
      conditionalCache = null;
      bulkNativeCache = null;
      bulkAsyncCache = null;
   }

   public int getThreadIndex() {
      return threadIndex;
   }

   public int getNodeIndex() {
      return nodeIndex;
   }

   public int getNumNodes() {
      return numNodes;
   }

   public Statistics getStats() {
      return stats;
   }

   public Random getRandom() {
      return random;
   }

   private class TransactionException extends Exception {
      private final long operationDuration;

      public TransactionException(long duration, Exception cause) {
         super(cause);
         this.operationDuration = duration;
      }

      public long getOperationDuration() {
         return operationDuration;
      }
   }

   private long startTransaction() throws TransactionException {
      long start = System.nanoTime();
      try {
         ongoingTx.begin();
      } catch (Exception e) {
         long time = System.nanoTime() - start;
         log.error("Failed to start transaction", e);
         throw new TransactionException(time, e);
      }
      return System.nanoTime() - start;
   }

   private long endTransaction() throws TransactionException {
      long start = System.nanoTime();
      try {
         if (stage.commitTransactions) {
            ongoingTx.commit();
         } else {
            ongoingTx.rollback();
         }
      } catch (Exception e) {
         long time = System.nanoTime() - start;
         log.error("Failed to end transaction", e);
         throw new TransactionException(time, e);
      }
      return System.nanoTime() - start;
   }
}
