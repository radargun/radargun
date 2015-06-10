package org.radargun.stages.test;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Statistics;
import org.radargun.traits.Transactional;
import org.radargun.utils.TimeService;

/**
 * Each stressor operates according to its {@link OperationLogic logic} - the instance is private to each thread.
 * After finishing the {@linkplain OperationLogic#init(Stressor) init phase}, all stressors synchronously
 * execute logic's {@link OperationLogic#run(org.radargun.Operation) run} method until
 * the {@link Completion#moreToRun()} returns false.
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
   private final OperationSelector operationSelector;
   private final Completion completion;

   private boolean useTransactions;
   private int txRemainingOperations = 0;
   private long transactionDuration = 0;
   private Transactional.Transaction ongoingTx;
   private Statistics stats;
   private boolean started = false;

   public Stressor(TestStage stage, OperationLogic logic, int globalThreadIndex, int threadIndex) {
      super("Stressor-" + threadIndex);
      this.stage = stage;
      this.threadIndex = threadIndex;
      this.globalThreadIndex = globalThreadIndex;
      this.logic = logic;
      this.random = ThreadLocalRandom.current();
      this.completion = stage.getCompletion();
      this.operationSelector = stage.getOperationSelector();
   }

   private boolean recording() {
      return started && !stage.isFinished();
   }

   @Override
   public void run() {
      try {
         logic.init(this);
         stats = stage.createStatistics();

         runInternal();

      } catch (Exception e) {
         log.error("Unexpected error in stressor!", e);
         stage.setTerminated();
      } finally {
         if (stats != null) {
            stats.end();
         }
         logic.destroy();
      }
   }

   private void runInternal() {
      while (!stage.isTerminated()) {
         boolean started = stage.isStarted();
         if (started) {
            txRemainingOperations = 0;
            if (ongoingTx != null) {
               endTransactionAndRegisterStats(null);
            }
            stats.begin();
            this.started = started;
            break;
         }
         Operation operation = operationSelector.next(random);
         try {
            logic.run(operation);
         } catch (OperationLogic.RequestException e) {
            // the exception was already logged in makeRequest
         }
      }

      operationSelector.start();
      completion.start();
      int i = 0;
      for (;;) {
         Operation operation = operationSelector.next(random);
         if (!completion.moreToRun()) break;
         try {
            logic.run(operation);
         } catch (OperationLogic.RequestException e) {
            // the exception was already logged in makeRequest
         }
         i++;
         completion.logProgress(i);
      }

      if (txRemainingOperations > 0) {
         endTransactionAndRegisterStats(null);
      }
   }

   public <T> T wrap(T resource) {
      return ongoingTx.wrap(resource);
   }

   public Object makeRequest(Invocation invocation) throws OperationLogic.RequestException {
      return makeRequest(invocation, true);
   }

   public Object makeRequest(Invocation invocation, boolean countForTx) throws OperationLogic.RequestException {
      long startTxTime = 0;
      if (useTransactions && txRemainingOperations <= 0) {
         try {
            ongoingTx = stage.transactional.getTransaction();
            logic.transactionStarted();
            startTxTime = startTransaction();
            transactionDuration = startTxTime;
            txRemainingOperations = stage.transactionSize;
            if (recording()) stats.registerRequest(startTxTime, Transactional.BEGIN);
         } catch (TransactionException e) {
            if (recording()) stats.registerError(e.getOperationDuration(), Transactional.BEGIN);
            throw new OperationLogic.RequestException(e);
         }
      }

      Object result = null;
      boolean successful = true;
      Exception exception = null;
      long start = TimeService.nanoTime();
      long operationDuration;
      try {
         result = invocation.invoke();
         operationDuration = TimeService.nanoTime() - start;
         // make sure that the return value cannot be optimized away
         // however, we can't be 100% sure about reordering without
         // volatile writes/reads here
         Blackhole.consume(result);
         if (countForTx) {
            txRemainingOperations--;
         }
      } catch (Exception e) {
         operationDuration = TimeService.nanoTime() - start;
         log.warn("Error in request", e);
         successful = false;
         txRemainingOperations = 0;
         exception = e;
      }
      transactionDuration += operationDuration;

      if (useTransactions && txRemainingOperations <= 0) {
         endTransactionAndRegisterStats(stage.isSingleTxType() ? invocation.txOperation() : null);
      }
      if (recording()) {
         if (successful) {
            stats.registerRequest(operationDuration, invocation.operation());
         } else {
            stats.registerError(operationDuration, invocation.operation());
         }
      }
      if (exception != null) {
         throw new OperationLogic.RequestException(exception);
      }
      return result;
   }

   private void endTransactionAndRegisterStats(Operation singleTxOperation) {
      long start = TimeService.nanoTime();
      try {
         if (stage.commitTransactions) {
            ongoingTx.commit();
         } else {
            ongoingTx.rollback();
         }
         long endTxTime = TimeService.nanoTime() - start;
         transactionDuration += endTxTime;
         if (recording()) {
            stats.registerRequest(endTxTime, stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerRequest(transactionDuration, Transactional.DURATION);
            if (singleTxOperation != null) {
               stats.registerRequest(transactionDuration, singleTxOperation);
            }
         }
      } catch (Exception e) {
         long endTxTime = TimeService.nanoTime() - start;
         if (recording()) {
            stats.registerError(endTxTime, stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerError(transactionDuration + endTxTime, Transactional.DURATION);
            if (singleTxOperation != null) {
               stats.registerError(transactionDuration, singleTxOperation);
            }
         }
         if (stage.logTransactionExceptions) {
            log.error("Failed to end transaction", e);
         }
      } finally {
         clearTransaction();
      }
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

   public boolean isUseTransactions() {
      return useTransactions;
   }

   private long startTransaction() throws TransactionException {
      long start = TimeService.nanoTime();
      try {
         ongoingTx.begin();
      } catch (Exception e) {
         long time = TimeService.nanoTime() - start;
         log.error("Failed to start transaction", e);
         throw new TransactionException(time, e);
      }
      return TimeService.nanoTime() - start;
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
