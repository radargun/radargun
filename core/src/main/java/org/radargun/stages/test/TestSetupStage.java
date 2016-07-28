package org.radargun.stages.test;

import java.util.Random;

import org.radargun.DistStageAck;
import org.radargun.config.EnsureInSchema;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

@Stage(doc = "In this stage we setup target parameters for future test. The stage performs ramp-up, starting all needed threads.")
public abstract class TestSetupStage extends AbstractDistStage {
   @Property(doc = "Name of the test as used for reporting. Default is 'Test'.")
   public String testName = "Test";

   @Property(doc = "Minimum time for the ramp-up. Default is 0.", converter = TimeConverter.class)
   public long rampUpMinDuration = 0;

   @Property(doc = "Maximum time for the ramp-up. If the number of threads does not stabilize until this limit " +
      "consider the test failed. Default is unlimited.", converter = TimeConverter.class)
   public long rampUpMaxDuration = 0;

   @Property(doc = "How long does the test need to have constant number of threads in order to execute " +
      "the transition from ramp-up to steady-state. Default is 1 minute.", converter = TimeConverter.class)
   public long rampUpMinSteadyPeriod = 60000;

   @Property(doc = "Minimum number of threads waiting for next operation during ramp-up. Default is 4.")
   public int rampUpMinWaitingThreads = 4;

   @Property(doc = "Number of threads started at the beginning. Default is 1.")
   public int minThreads = 1;

   @Property(doc = "Maximum number of executing threads. Default is 128.")
   public int maxThreads = 128;

   @Property(doc = "Minimum delay between creating another thread. Default is 20 ms.", converter = TimeConverter.class)
   public long minThreadCreationDelay = 20;

   @Property(doc = "Whether an error from invocation should be logged as error. Default is true.")
   protected boolean logRequestExceptions = true;

   @Property(doc = "Whether an error from transaction commit/rollback should be logged as error. Default is true.")
   public boolean logTransactionExceptions = true;

   protected RunningTest runningTest;

   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }
      runningTest = (RunningTest) slaveState.get(RunningTest.nameFor(testName));
      if (runningTest == null) {
         runningTest = new RunningTest();
         slaveState.put(RunningTest.nameFor(testName), runningTest);
         slaveState.addServiceListener(runningTest);
      } else if (runningTest.isTerminated()) {
         return errorResponse("The test was terminated in previous iteration");
      }
      prepare();
      runningTest.setMinThreadCreationDelay(minThreadCreationDelay);
      runningTest.setMinWaitingThreads(rampUpMinWaitingThreads);
      runningTest.setMaxThreads(maxThreads);
      runningTest.setLogExceptions(logRequestExceptions, logTransactionExceptions);
      runningTest.updateSelector(createSelector());

      log.info("Starting test " + testName + " ramp-up");
      int minThreads = Math.max(this.minThreads, rampUpMinWaitingThreads + 1);
      while (runningTest.getUsedThreads() < minThreads) {
         // one of the started threads may already create his sibling;
         // fail silently if we attempt to reach the limit
         runningTest.addStressor(true);
      }
      int lastThreads = 0;
      long now = TimeService.currentTimeMillis();
      long lastThreadsChange = now;
      long startTime = now;
      while (!runningTest.isTerminated()) {
         int currentThreads = runningTest.getUsedThreads();
         if (currentThreads != lastThreads) {
            lastThreadsChange = now;
            lastThreads = currentThreads;
         }
         if (now < startTime + rampUpMinDuration || now < lastThreadsChange + rampUpMinSteadyPeriod) {
            if (rampUpMaxDuration > 0 && now > startTime + rampUpMaxDuration) {
               break;
            }
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               log.warn("Interruptions should not happen.", e);
            }
         } else {
            break;
         }
         now = TimeService.currentTimeMillis();
      }
      if (runningTest.isTerminated()) {
         return errorResponse("Test was terminated during ramp-up");
      } else if (rampUpMaxDuration > 0 && now >= startTime + rampUpMaxDuration) {
         return errorResponse("Ramp-up has not stabilized within timeout");
      } else if (runningTest.isReachedMax()) {
         return errorResponse("Max thread count reached during ramp-up");
      }
      return successfulResponse();
   }

   protected abstract SchedulingSelector<Conversation> createSelector();

   protected void prepare() {
   }

   @EnsureInSchema
   public static class InvocationSetting {
      @Property(doc = "Number of invocations of given operation per interval (see property interval), on each node. Default is 0.")
      public int invocations = 0;

      @Property(doc = "Size of the slot in milliseconds. Raising this risks having bursts" +
         "at the beginning of the interval. Default is 1 ms.", converter = TimeConverter.class)
      public long interval = 1;
   }

   @EnsureInSchema
   public static class TxInvocationSetting extends InvocationSetting {
      @Property(doc = "Number of operations in single transaction. Default is 1.")
      public int transactionSize = 1;

      @Property(doc = "Percentage of transactions committed (as opposed to rolled back). " +
         "Default is 1 - all transactions are committed.")
      public double commit = 1d;

      public boolean shouldCommit(Random random) {
         if (commit == 1d) return true;
         else if (commit == 0d) return false;
         else return random.nextDouble() < commit;
      }
   }
}
