package org.radargun.stages.test.legacy;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.test.Blackhole;
import org.radargun.stages.test.Invocation;
import org.radargun.stats.Request;
import org.radargun.stats.RequestSet;
import org.radargun.stats.Statistics;
import org.radargun.traits.Transactional;

/**
 * Each stressor operates according to its {@link OperationLogic logic} - the instance is private to each thread.
 * After finishing the {@linkplain OperationLogic#init(LegacyStressor) init phase}, all stressors synchronously
 * execute logic's {@link OperationLogic#run(org.radargun.Operation) run} method until
 * the {@link Completion#moreToRun()} returns false.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class LegacyStressor extends Thread {
   private static Log log = LogFactory.getLog(LegacyStressor.class);

   private final LegacyTestStage stage;
   private final int threadIndex;
   private final int globalThreadIndex;
   private final OperationLogic logic;
   private final Random random;
   private final OperationSelector operationSelector;
   private final Completion completion;
   private final boolean logTransactionExceptions;

   private boolean useTransactions;
   private int txRemainingOperations = 0;
   private RequestSet requests;
   private Transactional.Transaction ongoingTx;
   private Statistics stats;
   private boolean started = false;

   public LegacyStressor(LegacyTestStage stage, OperationLogic logic, int globalThreadIndex, int threadIndex, boolean logTransactionExceptions) {
      super("Stressor-" + threadIndex);
      this.stage = stage;
      this.threadIndex = threadIndex;
      this.globalThreadIndex = globalThreadIndex;
      this.logic = logic;
      this.random = ThreadLocalRandom.current();
      this.completion = stage.getCompletion();
      this.operationSelector = stage.getOperationSelector();
      this.logTransactionExceptions = logTransactionExceptions;
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
      try {
         // the operation selector needs to be started before any #next() call
         operationSelector.start();

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

         completion.start();
         int i = 0;
         while (!stage.isTerminated()) {
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
      } finally {
         if (txRemainingOperations > 0) {
            endTransactionAndRegisterStats(null);
         }
      }
   }

   public <T> T wrap(T resource) {
      return ongoingTx.wrap(resource);
   }

   public <T> T makeRequest(Invocation<T> invocation) throws OperationLogic.RequestException {
      return makeRequest(invocation, true);
   }

   public <T> T makeRequest(Invocation<T> invocation, boolean countForTx) throws OperationLogic.RequestException {
      if (useTransactions && txRemainingOperations <= 0) {
         try {
            ongoingTx = stage.transactional.getTransaction();
            logic.transactionStarted();
            if (recording()) {
               requests = stats.requestSet();
            }
            Request beginRequest = startTransaction();
            if (requests != null && beginRequest != null) {
               requests.add(beginRequest);
            }
            txRemainingOperations = stage.transactionSize;
         } catch (TransactionException e) {
            throw new OperationLogic.RequestException(e);
         }
      }

      T result = null;
      Exception exception = null;
      Request request = recording() ? stats.startRequest() : null;
      try {
         result = invocation.invoke();
         succeeded(request, invocation.operation());
         // make sure that the return value cannot be optimized away
         // however, we can't be 100% sure about reordering without
         // volatile writes/reads here
         Blackhole.consume(result);
         if (countForTx) {
            txRemainingOperations--;
         }
      } catch (Exception e) {
         failed(request, invocation.operation());
         log.warn("Error in request", e);
         txRemainingOperations = 0;
         exception = e;
      }
      if (requests != null && request != null && recording()) {
         requests.add(request);
      }

      if (useTransactions && txRemainingOperations <= 0) {
         endTransactionAndRegisterStats(stage.isSingleTxType() ? invocation.txOperation() : null);
      }
      if (exception != null) {
         throw new OperationLogic.RequestException(exception);
      }
      return result;
   }

   public <T> void succeeded(Request request, Operation operation) {
      if (request != null) {
         if (recording()) {
            request.succeeded(operation);
         } else {
            request.discard();
         }
      }
   }

   public <T> void failed(Request request, Operation operation) {
      if (request != null) {
         if (recording()) {
            request.failed(operation);
         } else {
            request.discard();
         }
      }
   }

   private void endTransactionAndRegisterStats(Operation singleTxOperation) {
      Request commitRequest = recording() ? stats.startRequest() : null;
      try {
         if (stage.commitTransactions) {
            ongoingTx.commit();
         } else {
            ongoingTx.rollback();
         }
         succeeded(commitRequest, stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
      } catch (Exception e) {
         failed(commitRequest, stage.commitTransactions ? Transactional.COMMIT : Transactional.ROLLBACK);
         if (logTransactionExceptions) {
            log.error("Failed to end transaction", e);
         }
      } finally {
         if (requests != null) {
            if (recording()) {
               requests.add(commitRequest);
               requests.finished(commitRequest.isSuccessful(), Transactional.DURATION);
               if (singleTxOperation != null) {
                  requests.finished(commitRequest.isSuccessful(), singleTxOperation);
               }
            } else {
               requests.discard();
            }
            requests = null;
         }
         clearTransaction();
      }
   }

   public void setUseTransactions(boolean useTransactions) {
      this.useTransactions = useTransactions;
   }

   private void clearTransaction() {
      logic.transactionEnded();
      ongoingTx = null;
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

   private Request startTransaction() throws TransactionException {
      Request request = recording() ? stats.startRequest() : null;
      try {
         ongoingTx.begin();
         if (request != null) {
            request.succeeded(Transactional.BEGIN);
         }
         return request;
      } catch (Exception e) {
         if (request != null) {
            request.failed(Transactional.BEGIN);
         }
         log.error("Failed to start transaction", e);
         throw new TransactionException(request, e);
      }
   }

   private class TransactionException extends Exception {
      private final Request request;

      public TransactionException(Request request, Exception cause) {
         super(cause);
         this.request = request;
      }

      public Request getRequest() {
         return request;
      }
   }
}
