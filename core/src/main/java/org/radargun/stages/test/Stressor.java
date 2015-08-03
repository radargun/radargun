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
 * Thread that generates the load in {@link TestStage}, created in {@link TestSetupStage}
 * and kept in {@link RunningTest}. The thread can either execute in ramp-up state (without
 * any statistics recorded) or in steady-state, where statistics are recorded.
 * During execution, the thread queries {@link ConversationSelector} for the next
 * {@link Conversation} and then executes it's {@link Conversation#run(Stressor)} method
 * which in turn calls {@link #makeRequest(Invocation)} with certain {@link Invocation}.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Stressor extends Thread {
   private static Log log = LogFactory.getLog(Stressor.class);

   private final RunningTest test;
   private Random random;

   private ConversationSelector selector;
   private Statistics stats;

   private long transactionDuration = 0;
   private boolean logTransactionExceptions = true;

   public Stressor(int threadIndex, RunningTest test, boolean logTransactionExceptions) {
      super("Stressor-" + threadIndex);
      this.test = test;
      this.logTransactionExceptions = logTransactionExceptions;
   }

   private boolean recording() {
      return stats != null;
   }

   @Override
   public void run() {
      // we cannot set this in constructor since the constructor is called in sc-main thread!
      random = ThreadLocalRandom.current();
      try {
         while (!test.isFinished()) {
            // one cycle of this loop should match to a stage.

            // this is the ramp-up state
            while (!test.isFinished() && !test.isSteadyState()) {
               selector = test.getSelector();
               try {
                  Conversation conversation = selector.next();
                  conversation.run(this);
               } catch (InterruptedException e) {
                  // just react on test state
               } catch (RequestException e) {
                  // the exception was already logged in makeRequest
               }
            }

            stats = test.createStatistics();
            if (stats == null) {
               // cannot create statistics because the test did not started at all
               break;
            }
            stats.begin();
            try {
               // and this is the steady state
               while (test.isSteadyState()) {
                  if (selector == null) {
                     // this means that the thread was not created during ramp-up state,
                     // since it did not grab the selector
                     selector = test.getSelector();
                  }
                  try {
                     Conversation conversation = selector.next();
                     conversation.run(this);
                  } catch (InterruptedException e) {
                     // the test is interrupted
                  } catch (RequestException e) {
                     // the exception was already logged in makeRequest
                  }
               }
            } finally {
               stats.end();
               test.recordStatistics(stats);
               stats = null;
            }
         }
      } catch (Exception e) {
         log.error("Unexpected error in stressor!", e);
         test.setTerminated();
      }
   }

   /**
    * Main method called from conversation in order to execute an operation with registered duration.
    *
    * @param invocation
    * @param <T>
    * @return
    */
   public <T> T makeRequest(Invocation<T> invocation) {
      T result = null;
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
      } catch (Exception e) {
         operationDuration = TimeService.nanoTime() - start;
         log.warn("Error in request", e);
         successful = false;
         exception = e;
      }
      transactionDuration += operationDuration;

      if (recording()) {
         if (successful) {
            stats.registerRequest(operationDuration, invocation.operation());
         } else {
            stats.registerError(operationDuration, invocation.operation());
         }
      }
      if (exception != null) {
         throw new RequestException(exception);
      }
      return result;
   }

   public void startTransaction(Transactional.Transaction transaction) {
      long start = TimeService.nanoTime();
      try {
         transaction.begin();
         long time = TimeService.nanoTime() - start;
         transactionDuration = time;
         if (recording()) stats.registerRequest(time, Transactional.BEGIN);
      } catch (Exception e) {
         long time = TimeService.nanoTime() - start;
         log.error("Failed to start transaction", e);
         if (recording()) stats.registerError(time, Transactional.BEGIN);
         throw e;
      }
   }

   public void commitTransaction(Transactional.Transaction transaction, Operation txOperation) {
      endTransaction(transaction, true, txOperation);
   }

   public void rollbackTransaction(Transactional.Transaction transaction, Operation txOperation) {
      endTransaction(transaction, false, txOperation);
   }

   private void endTransaction(Transactional.Transaction transaction, boolean commit, Operation txOperation) {
      long start = TimeService.nanoTime();
      try {
         if (commit) {
            transaction.commit();
         } else {
            transaction.rollback();
         }
         long endTxTime = TimeService.nanoTime() - start;
         transactionDuration += endTxTime;
         if (recording()) {
            stats.registerRequest(endTxTime, commit ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerRequest(transactionDuration, Transactional.DURATION);
            if (txOperation != null) {
               stats.registerRequest(transactionDuration, txOperation);
            }
         }
      } catch (Exception e) {
         long endTxTime = TimeService.nanoTime() - start;
         if (recording()) {
            stats.registerError(endTxTime, commit ? Transactional.COMMIT : Transactional.ROLLBACK);
            stats.registerError(transactionDuration + endTxTime, Transactional.DURATION);
            if (txOperation != null) {
               stats.registerError(transactionDuration, txOperation);
            }
         }
         if (logTransactionExceptions) {
            log.error("Failed to end transaction", e);
         }
      }
   }

   public Random getRandom() {
      return random;
   }

   // this is a runtime exception since we don't want to declare in in Conversation#run(), because it's only thrown
   // from makeRequest
   private static class RequestException extends RuntimeException {
      public RequestException(Throwable cause) {
         super(cause);
      }
   }
}
