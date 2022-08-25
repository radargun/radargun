package org.radargun.stages.test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Request;
import org.radargun.stats.RequestSet;
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
   private final boolean logTransactionExceptions;
   private long thinkTime;

   private boolean useTransactions;
   private int txRemainingOperations = 0;
   private RequestSet requests;
   private Transactional.Transaction ongoingTx;
   private Statistics stats;
   private boolean started = false;
   private CountDownLatch threadCountDown;
   private final AtomicBoolean continueRunning;

   // uniform rate limiter
   final long uniformRateLimiterOpsPerNano;
   long uniformRateLimiterOpIndex = 0;
   long uniformRateLimiterStart = Long.MIN_VALUE;

   final boolean reportLatencyAsServiceTime;

   public Stressor(TestStage stage, OperationLogic logic, int globalThreadIndex, int threadIndex, CountDownLatch threadCountDown, AtomicBoolean continueRunning) {
      super("Stressor-" + threadIndex);
      this.stage = stage;
      this.threadIndex = threadIndex;
      this.globalThreadIndex = globalThreadIndex;
      this.logic = logic;
      this.random = ThreadLocalRandom.current();
      this.completion = stage.getCompletion();
      this.operationSelector = stage.getOperationSelector();
      this.logTransactionExceptions = stage.logTransactionExceptions;
      this.threadCountDown = threadCountDown;
      this.thinkTime = stage.thinkTime;
      this.uniformRateLimiterOpsPerNano = TimeUnit.MILLISECONDS.toNanos(stage.cycleTime);
      this.reportLatencyAsServiceTime = stage.reportLatencyAsServiceTime;
      this.continueRunning = continueRunning;
   }

   private boolean recording() {
      return this.started;
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
      boolean counted = false;
      try {
         // the operation selector needs to be started before any #next() call
         operationSelector.start();

         while (!stage.isTerminated() && continueRunning.get()) {
            boolean started = stage.isStarted();
            if (started) {
               txRemainingOperations = 0;
               if (ongoingTx != null) {
                  endTransactionAndRegisterStats(null);
               }
               break;
            } else {
               if (!counted) {
                  threadCountDown.countDown();
                  counted = true;
               }
               Operation operation = operationSelector.next(random);
               try {
                  logic.run(operation);
                  if (thinkTime > 0)
                    sleep(thinkTime);
               } catch (OperationLogic.RequestException e) {
                  if (stage.exitOnFailure) {
                     // it will stop all stressor
                     continueRunning.set(false);
                     throw new IllegalStateException("Benchmark has exit on failure configured");
                  }
                  // the exception was already logged in makeRequest
               } catch (InterruptedException e) {
                  log.trace("Stressor interrupted.", e);
                  interrupt();
               }
            }
         }

         uniformRateLimiterStart = TimeService.nanoTime();
         stats.begin();
         this.started = true;
         completion.start();
         int i = 0;
         while (!stage.isTerminated() && continueRunning.get()) {
            Operation operation = operationSelector.next(random);
            if (!completion.moreToRun()) break;
            try {
               logic.run(operation);
               if (thinkTime > 0)
                  sleep(thinkTime);
            } catch (OperationLogic.RequestException e) {
               if (stage.exitOnFailure) {
                  // it will stop all stressor
                  continueRunning.set(false);
                  throw new IllegalStateException("Benchmark has exit on failure configured");
               }
               // the exception was already logged in makeRequest
            } catch (InterruptedException e) {
               log.trace("Stressor interrupted.", e);
               interrupt();
            }
            i++;
            completion.logProgress(i);
         }
      } finally {
         // before, the finishCountDown.countDown() was inside the completion.moreToRun() method
         // if we have a custom logic in the while and the iteration stopped, the CountDownLatch wasn't called
         stage.getCompletionHandler().run();
         this.started = false;
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
      Request request = nextRequest();
      Operation operation = null;
      try {
         result = invocation.invoke();
         operation = invocation.operation();
         if (useTransactions) {
            succeeded(request, invocation.txOperation());
         } else {
            succeeded(request, operation);
         }
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
         endTransactionAndRegisterStats(operation);
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

   /*
    * We would like to compare the non-TX and TX operation in the same chart
    * For example:
    * BasicOperations.Get = cache.get
    * Transactional.Duration = tx.begin, cache.get, tx.commit
    *
    * I would like to compare (tx.begin, cache.get, tx.commit) VS (cache.get).
    *
    * This is the reason why we are reporting the noTxOperation as `requests.finished(noTxOperation)`
    *
    * For the tx cache the values in BasicOperations.Get must be the same as Transactional.Duration
    */
   private void endTransactionAndRegisterStats(Operation noTxOperation) {
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
               if (noTxOperation != null) {
                  requests.finished(commitRequest.isSuccessful(), noTxOperation);
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

   private Request nextRequest() {
      Request request = null;
      if (recording()) {
         if (uniformRateLimiterOpsPerNano > 0) {
            long intendedTime = uniformRateLimiterStart + (uniformRateLimiterOpIndex++) * uniformRateLimiterOpsPerNano;
            long now;
            while ((now = System.nanoTime()) < intendedTime)
               LockSupport.parkNanos(intendedTime - now);
            request = stats.startRequest(reportLatencyAsServiceTime ? System.nanoTime() : intendedTime);
         } else {
            request = stats.startRequest();
         }
      }
      return request;
   }
}
