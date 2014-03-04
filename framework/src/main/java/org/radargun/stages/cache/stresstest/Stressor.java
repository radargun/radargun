package org.radargun.stages.cache.stresstest;

import java.util.Map;
import java.util.Set;

import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.Queryable;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Operation;
import org.radargun.stats.Statistics;
import org.radargun.traits.Transactional;

/**
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
   private BasicOperations.Cache basicCache;
   private ConditionalOperations.Cache conditionalCache;
   private BulkOperations.Cache bulkNativeCache;
   private BulkOperations.Cache bulkAsyncCache;
   private Transactional.Resource txCache;
   private Queryable queryable;

   public Stressor(StressTestStage stage, OperationLogic logic, int threadIndex, int nodeIndex, int numNodes) {
      super("Stressor-" + threadIndex);
      this.stage = stage;
      this.threadIndex = threadIndex;
      this.nodeIndex = nodeIndex;
      this.numNodes = numNodes;
      this.logic = logic;
      synchronizer = stage.getSynchronizer();
      completion = stage.getCompletion();

      String cacheName = stage.bucketPolicy.getBucketName(threadIndex);
      basicCache = stage.basicOperations == null ? null : stage.basicOperations.getCache(cacheName);
      conditionalCache = stage.conditionalOperations == null ? null : stage.conditionalOperations.getCache(cacheName);
      if (stage.bulkOperations != null) {
         bulkNativeCache = stage.bulkOperations.getCache(cacheName, false);
         bulkAsyncCache = stage.bulkOperations.getCache(cacheName, true);
      }
      useTransactions = stage.useTransactions != null ? stage.useTransactions :
            stage.transactional == null ? false : stage.transactional.isTransactional(cacheName);
      txCache = useTransactions ? stage.transactional.getResource(cacheName) : txCache;
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
            if (!stage.isTerminated()) {
               logic.init(threadIndex, nodeIndex, numNodes);
            }
            stats = stage.createStatistics();
            synchronizer.slavePhaseEnd();
            synchronizer.slavePhaseStart();
            try {
               if (!stage.isTerminated()) {
                  log.trace("Starting thread: " + getName());
                  runInternal();
               }
            } catch (Exception e) {
               stage.setTerminated();
               log.error("Unexpected error in stressor!", e);
            } finally {
               synchronizer.slavePhaseEnd();
            }
         }
      } catch (Exception e) {
         log.error("Unexpected error in stressor!", e);
      }
   }

   private void runInternal() {
      int i = 0;
      while (completion.moreToRun()) {
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
            stats.registerRequest(transactionDuration + endTxTime, 0, Operation.TRANSACTION);
         } catch (TransactionException e) {
            stats.registerError(transactionDuration + e.getOperationDuration(), 0, Operation.TRANSACTION);
         }
         transactionDuration = 0;
      }
   }

   public Object makeRequest(Operation operation, Object... keysAndValues) throws OperationLogic.RequestException {
      long startTxTime = 0;
      if (useTransactions && txRemainingOperations <= 0) {
         try {
            startTxTime = startTransaction();
            transactionDuration = startTxTime;
            txRemainingOperations = stage.transactionSize;
         } catch (TransactionException e) {
            stats.registerError(e.getOperationDuration(), 0, Operation.TRANSACTION);
            return null;
         }
      }

      Object result = null;
      boolean successful = true;
      Exception exception = null;
      long start = System.nanoTime();
      long operationDuration;
      try {
         switch (operation) {
            case GET:
            case GET_NULL:
               result = basicCache.get(keysAndValues[0]);
               operation = (result != null ? Operation.GET : Operation.GET_NULL);
               break;
            case PUT:
               basicCache.put(keysAndValues[0], keysAndValues[1]);
               break;
            case QUERY:
               result = queryable.executeQuery((Map<String, Object>) keysAndValues[0]);
               break;
            case REMOVE:
               result = basicCache.remove(keysAndValues[0]);
               break;
            case REMOVE_VALID:
               successful = conditionalCache.remove(keysAndValues[0], keysAndValues[1]);
               break;
            case REMOVE_INVALID:
               successful = !conditionalCache.remove(keysAndValues[0], keysAndValues[1]);
               break;
            case PUT_IF_ABSENT_IS_ABSENT:
               result = conditionalCache.putIfAbsent(keysAndValues[0], keysAndValues[1]);
               successful = result == null;
               break;
            case PUT_IF_ABSENT_NOT_ABSENT:
               result = conditionalCache.putIfAbsent(keysAndValues[0], keysAndValues[1]);
               successful = keysAndValues[2].equals(result);
               break;
            case REPLACE_VALID:
               successful = conditionalCache.replace(keysAndValues[0], keysAndValues[1], keysAndValues[2]);
               break;
            case REPLACE_INVALID:
               successful = !conditionalCache.replace(keysAndValues[0], keysAndValues[1], keysAndValues[2]);
               break;
            case GET_ALL:
               result = bulkNativeCache.getAll((Set<Object>) keysAndValues[0]);
               break;
            case GET_ALL_VIA_ASYNC:
               result = bulkAsyncCache.getAll((Set<Object>) keysAndValues[0]);
               break;
            case PUT_ALL:
               bulkNativeCache.putAll((Map<Object, Object>) keysAndValues[0]);
               break;
            case PUT_ALL_VIA_ASYNC:
               bulkAsyncCache.putAll((Map<Object, Object>) keysAndValues[0]);
               break;
            case REMOVE_ALL:
               bulkNativeCache.removeAll((Set<Object>) keysAndValues[0]);
               break;
            case REMOVE_ALL_VIA_ASYNC:
               bulkAsyncCache.removeAll((Set<Object>) keysAndValues[0]);
               break;
            default:
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
            stats.registerRequest(transactionDuration + endTxTime, 0, Operation.TRANSACTION);
         } catch (TransactionException e) {
            endTxTime = e.getOperationDuration();
            stats.registerError(transactionDuration + endTxTime, 0, Operation.TRANSACTION);
         }
      }
      if (successful) {
         stats.registerRequest(operationDuration, startTxTime + endTxTime, operation);
      } else {
         stats.registerError(operationDuration, startTxTime + endTxTime, operation);
      }
      if (exception != null) {
         throw new OperationLogic.RequestException(exception);
      }
      return result;
   }

   public int getThreadIndex() {
      return threadIndex;
   }

   public Statistics getStats() {
      return stats;
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
         txCache.startTransaction();
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
         txCache.endTransaction(stage.commitTransactions);
      } catch (Exception e) {
         long time = System.nanoTime() - start;
         log.error("Failed to end transaction", e);
         throw new TransactionException(time, e);
      }
      return System.nanoTime() - start;
   }
}
