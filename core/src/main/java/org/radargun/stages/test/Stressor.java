package org.radargun.stages.test;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Statistics;
import org.radargun.traits.Transactional;

/**
 * Each stressor operates according to its {@link OperationLogic logic} - the instance is private to each thread.
 * After finishing the {@linkplain OperationLogic#init(Stressor) init phase}, all stressors synchronously
 * execute logic's {@link OperationLogic#run() run} method until
 * the {@link AbstractCompletion#moreToRun(int)} returns false.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Stressor extends Thread {
   private static Log log = LogFactory.getLog(Stressor.class);

   private final TestStage stage;
   private final int threadIndex;
   private final int globalThreadIndex;
   private final OperationLogic logic;
   private final Random random;
   private final Completion completion;

   private boolean useTransactions;
   private int txRemainingOperations = 0;
   private long transactionDuration = 0;
   private Transactional.Transaction ongoingTx;
   private Statistics stats;

   public Stressor(TestStage stage, OperationLogic logic, int globalThreadIndex, int threadIndex) {
      super("Stressor-" + threadIndex);
      this.stage = stage;
      this.threadIndex = threadIndex;
      this.globalThreadIndex = globalThreadIndex;
      this.logic = logic;
      this.random = ThreadLocalRandom.current();
      this.completion = stage.getCompletion();
   }

   @Override
   public void run() {
      try {
         logic.init(this);
         stats = stage.createStatistics();
         stage.getStartLatch().await();

         stats.begin();
         runInternal();

      } catch (Exception e) {
         log.error("Unexpected error in stressor!", e);
         stage.setTerminated();
      } finally {
         if (stats != null) {
            stats.end();
         }
         stage.getFinishLatch().countDown();
      }
   }

   private void runInternal() {
      int i = 0;
      while (completion.moreToRun(i)) {
         Object result = null;
         try {
            if (useTransactions && txRemainingOperations <= 0) {
               ongoingTx = stage.transactional.getTransaction();
               logic.transactionStarted();
            }
            result = logic.run();
         } catch (OperationLogic.RequestException e) {
            // the exception was already logged in makeRequest
         }
         i++;
         completion.logProgress(i);
         if (i % stage.logPeriod == 0) TestStage.avoidJit(result);
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

   public <T> T wrap(T resource) {
      return ongoingTx.wrap(resource);
   }

   public Object makeRequest(Invocation invocation) throws OperationLogic.RequestException {
      long startTxTime = 0;
      if (useTransactions && txRemainingOperations <= 0) {
         try {
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
         result = invocation.invoke();
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
         stats.registerRequest(operationDuration, invocation.operation());
         if (useTransactions && stage.transactionSize == 1) {
            stats.registerRequest(startTxTime + operationDuration + endTxTime, invocation.txOperation());
         }
      } else {
         stats.registerError(operationDuration, invocation.operation());
         if (useTransactions && stage.transactionSize == 1) {
            stats.registerError(startTxTime + operationDuration + endTxTime, invocation.txOperation());
         }
      }
      if (exception != null) {
         throw new OperationLogic.RequestException(exception);
      }
      return result;
   }

   public void setUseTransactions(boolean useTransactions) {
      this.useTransactions = useTransactions;
   }

   private void clearTransaction() {
      ongoingTx = null;
      logic.transactionEnded();
   }

   public int getThreadIndex() {
      return threadIndex;
   }

   public int getGlobalThreadIndex() {
      return globalThreadIndex;
   }

   public Statistics getStats() {
      return stats;
   }

   public OperationLogic getLogic() {
      return logic;
   }

   public Random getRandom() {
      return random;
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
}
