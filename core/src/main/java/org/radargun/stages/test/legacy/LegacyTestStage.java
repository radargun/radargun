package org.radargun.stages.test.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.IterationData;
import org.radargun.reporting.Report;
import org.radargun.stages.test.BaseTestStage;
import org.radargun.stages.test.TransactionMode;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.Projections;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Base for test spawning several threads and benchmark of operations executed in those.")
public abstract class LegacyTestStage extends BaseTestStage {

   @Property(doc = "The number of threads executing on each node. You have to set either this or 'total-threads'. No default.")
   protected int numThreadsPerNode = 0;

   @Property(doc = "Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.")
   protected int totalThreads = 0;

   @Property(doc = "Specifies if the requests should be explicitly wrapped in transactions. " +
         "Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if " +
         "the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.")
   protected TransactionMode useTransactions = TransactionMode.IF_TRANSACTIONAL;

   @Property(doc = "Specifies whether the transactions should be committed (true) or rolled back (false). " +
         "Default is true")
   protected boolean commitTransactions = true;

   @Property(doc = "Number of requests in one transaction. Default is 1.")
   protected int transactionSize = 1;

   @Property(doc = "Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, " +
         "there is still the random ramp-up delay. Default is false.")
   protected boolean synchronousRequests = false;

   @Property(doc = "Max duration of the test. Default is infinite.", converter = TimeConverter.class)
   protected long timeout = 0;

   @Property(doc = "Delay to let all threads start executing operations. Default is 0.", converter = TimeConverter.class)
   protected long rampUp = 0;

   @Property(doc = "Whether an error from transaction commit/rollback should be logged as error. Default is true.")
   protected boolean logTransactionExceptions = true;

   @InjectTrait
   protected Transactional transactional;

   private CountDownLatch finishCountDown;
   private Completion completion;
   private OperationSelector operationSelector;

   protected volatile boolean started = false;
   protected volatile boolean finished = false;
   protected volatile boolean terminated = false;

   @Init
   public void init() {
      if (totalThreads <= 0 && numThreadsPerNode <= 0) throw new IllegalStateException("You have to set either total-threads or num-threads-per-node.");
      if (totalThreads > 0 && numThreadsPerNode > 0) throw new IllegalStateException("You have to set only one ot total-threads, num-threads-per-node");
      if (totalThreads < 0 || numThreadsPerNode < 0) throw new IllegalStateException("Number of threads can't be < 0");
   }

   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }
      prepare();
      try {
         long startNanos = TimeService.nanoTime();
         log.info("Starting test " + testName);
         List<LegacyStressor> stressors = execute();
         destroy();
         log.info("Finished test. Test duration is: " + Utils.getNanosDurationString(TimeService.nanoTime() - startNanos));
         return newStatisticsAck(stressors);
      } catch (Exception e) {
         return errorResponse("Exception while initializing the test", e);
      }
   }

   /**
    * To be overridden in inheritors.
    */
   protected void prepare() {
   }

   /**
    * To be overridden in inheritors.
    */
   protected void destroy() {
   }

   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Report.Test test = getTest(amendTest);
      testIteration = test == null ? 0 : test.getIterations().size();
      // we cannot use aggregated = createStatistics() since with PeriodicStatistics the merge would fail
      Statistics aggregated = null;
      int threads = 0;
      for (StatisticsAck ack : Projections.instancesOf(acks, StatisticsAck.class)) {
         if (ack.iterations != null) {
            int i = getTestIteration();
            for (List<Statistics> threadStats : ack.iterations) {
               if (test != null) {
                  // TODO: this looks like we could get same iteration value for all iterations reported
                  String iterationValue = resolveIterationValue();
                  if (iterationValue != null) {
                     test.setIterationValue(i, iterationValue);
                  }
                  test.addStatistics(i++, ack.getSlaveIndex(), threadStats);
               }
               threads = Math.max(threads, threadStats.size());
               for (Statistics s : threadStats) {
                  if (aggregated == null) {
                     aggregated = s.copy();
                  } else {
                     aggregated.merge(s);
                  }
               }
            }
         } else {
            log.trace("No statistics received from slave: " + ack.getSlaveIndex());
         }
      }
      if (checkRepeatCondition(aggregated)) {
         return StageResult.SUCCESS;
      } else {
         return StageResult.BREAK;
      }
   }

   public List<LegacyStressor> execute() {
      long startTime = TimeService.currentTimeMillis();
      completion = createCompletion();
      finishCountDown = new CountDownLatch(1);
      completion.setCompletionHandler(new Runnable() {
         @Override
         public void run() {
            finished = true;
            finishCountDown.countDown();
         }
      });
      operationSelector = wrapOperationSelector(createOperationSelector());

      List<LegacyStressor> stressors = startStressors();

      if (rampUp > 0) {
         try {
            Thread.sleep(rampUp);
         } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted during ramp-up.", e);
         }
      }
      started = true;
      try {
         if (timeout > 0) {
            long waitTime = getWaitTime(startTime);
            if (waitTime <= 0) {
               throw new TestTimeoutException();
            } else {
               if (!finishCountDown.await(waitTime, TimeUnit.MILLISECONDS)) {
                  throw new TestTimeoutException();
               }
            }
         } else {
            finishCountDown.await();
         }
      } catch (InterruptedException e) {
         throw new IllegalStateException("Unexpected interruption", e);
      }
      for (Thread stressorThread : stressors) {
         try {
            if (timeout > 0) {
               long waitTime = getWaitTime(startTime);
               if (waitTime <= 0) throw new TestTimeoutException();
               stressorThread.join(waitTime);
            } else {
               stressorThread.join();
            }
         } catch (InterruptedException e) {
            throw new TestTimeoutException(e);
         }
      }
      return stressors;
   }

   protected Completion createCompletion() {
      Completion completion = new TimeStressorCompletion(duration);
      return completion;
   }

   protected OperationSelector createOperationSelector() {
      return OperationSelector.DUMMY;
   }

   protected OperationSelector wrapOperationSelector(OperationSelector operationSelector) {
      if (synchronousRequests) {
         operationSelector = new SynchronousOperationSelector(operationSelector);
      }
      return operationSelector;
   }

   protected List<LegacyStressor> startStressors() {
      int myFirstThread = getFirstThreadOn(slaveState.getSlaveIndex());
      int myNumThreads = getNumThreadsOn(slaveState.getSlaveIndex());

      List<LegacyStressor> stressors = new ArrayList<>();
      for (int threadIndex = stressors.size(); threadIndex < myNumThreads; threadIndex++) {
         LegacyStressor stressor = new LegacyStressor(this, getLogic(), myFirstThread + threadIndex, threadIndex, logTransactionExceptions);
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Started " + stressors.size() + " stressor threads.");
      return stressors;
   }

   protected DistStageAck newStatisticsAck(List<LegacyStressor> stressors) {
      List<List<Statistics>> results = gatherResults(stressors, new StatisticsResultRetriever());
      return new StatisticsAck(slaveState, results);
   }

   protected <T> List<List<T>> gatherResults(List<LegacyStressor> stressors, ResultRetriever<T> retriever) {
      List<T> results = new ArrayList<>(stressors.size());
      for (LegacyStressor stressor : stressors) {
         T result = retriever.getResult(stressor);
         if (result != null) { // stressor could have crashed during initialization
            results.add(result);
         }
      }

      List<List<T>> all = new ArrayList<>();
      all.add(new ArrayList<T>());
      /* expand the iteration statistics into iterations */
      for (T result : results) {
         if (result instanceof IterationData) {
            int iteration = 0;
            for (IterationData.Iteration<T> it : ((IterationData<T>) result).getIterations()) {
               while (iteration >= all.size()) {
                  all.add(new ArrayList<T>(results.size()));
               }
               addResult(all.get(iteration++), it.data, retriever);
            }
         } else {
            addResult(all.get(0), result, retriever);
         }
      }
      return all;
   }

   private <T> void addResult(List<T> results, T result, ResultRetriever<T> retriever) {
      if (mergeThreadStats) {
         if (results.isEmpty()) {
            results.add(result);
         } else {
            retriever.mergeResult(results.get(0), result);
         }
      } else {
         results.add(result);
      }
   }

   private long getWaitTime(long startTime) {
      return startTime + timeout - TimeService.currentTimeMillis();
   }

   public int getTotalThreads() {
      if (totalThreads > 0) {
         return totalThreads;
      } else if (numThreadsPerNode > 0) {
         return getExecutingSlaves().size() * numThreadsPerNode;
      } else throw new IllegalStateException();
   }

   public int getFirstThreadOn(int slave) {
      List<Integer> executingSlaves = getExecutingSlaves();
      int execId = executingSlaves.indexOf(slave);
      if (numThreadsPerNode > 0) {
         return execId * numThreadsPerNode;
      } else if (totalThreads > 0) {
         return execId * totalThreads / executingSlaves.size();
      } else {
         throw new IllegalStateException();
      }
   }

   public int getNumThreadsOn(int slave) {
      List<Integer> executingSlaves = getExecutingSlaves();
      if (numThreadsPerNode > 0) {
         return executingSlaves.contains(slaveState.getSlaveIndex()) ? numThreadsPerNode : 0;
      } else if (totalThreads > 0) {
         int execId = executingSlaves.indexOf(slave);
         return (execId + 1) * totalThreads / executingSlaves.size() - execId * totalThreads / executingSlaves.size();
      } else {
         throw new IllegalStateException();
      }
   }

   protected Statistics createStatistics() {
      return statisticsPrototype.copy();
   }

   public boolean isStarted() {
      return started;
   }

   public boolean isFinished() {
      return finished;
   }

   public boolean isTerminated() {
      return terminated;
   }

   public void setTerminated() {
      terminated = true;
      finishCountDown.countDown();
   }

   public Completion getCompletion() {
      return completion;
   }

   public OperationSelector getOperationSelector() {
      return operationSelector;
   }

   public boolean useTransactions(String resourceName) {
      return useTransactions.use(transactional, resourceName, transactionSize);
   }

   public abstract OperationLogic getLogic();

   public boolean isSingleTxType() {
      return transactionSize == 1;
   }

   protected interface ResultRetriever<T> {
      T getResult(LegacyStressor stressor);
      void mergeResult(T into, T that);
   }

   protected static class StatisticsResultRetriever implements ResultRetriever<Statistics> {
      public StatisticsResultRetriever() {}

      @Override
      public Statistics getResult(LegacyStressor stressor) {
         return stressor.getStats();
      }

      @Override
      public void mergeResult(Statistics into, Statistics that) {
         into.merge(that);
      }
   }

   private class TestTimeoutException extends RuntimeException {
      public TestTimeoutException() {
      }

      public TestTimeoutException(Throwable cause) {
         super(cause);
      }
   }

   protected static class StatisticsAck extends DistStageAck {
      public final List<List<Statistics>> iterations;

      public StatisticsAck(SlaveState slaveState, List<List<Statistics>> iterations) {
         super(slaveState);
         this.iterations = iterations;
      }
   }
}
