package org.radargun.stages.test.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.Version;
import org.radargun.config.Init;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.test.BaseTestStage;
import org.radargun.stages.test.TransactionMode;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

@Namespace(LegacyTestStage.NAMESPACE)
@Stage(doc = "Base for test spawning several threads and benchmark of operations executed in those.")
public abstract class LegacyTestStage extends BaseTestStage {
   public static final String NAMESPACE = "urn:radargun:stages:legacy:" + Version.SCHEMA_VERSION;

   @Property(doc = "The number of threads executing on each node. You have to set either this or 'total-threads'. No default.")
   public int numThreadsPerNode = 0;

   @Property(doc = "Total number of threads across whole cluster. You have to set either this or 'num-threads-per-node'. No default.")
   public int totalThreads = 0;

   @Property(doc = "Specifies if the requests should be explicitly wrapped in transactions. " +
      "Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if " +
      "the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.")
   public TransactionMode useTransactions = TransactionMode.IF_TRANSACTIONAL;

   @Property(doc = "Specifies whether the transactions should be committed (true) or rolled back (false). " +
      "Default is true")
   public boolean commitTransactions = true;

   @Property(doc = "Number of requests in one transaction. Default is 1.")
   public int transactionSize = 1;

   @Property(doc = "Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, " +
      "there is still the random ramp-up delay. Default is false.")
   public boolean synchronousRequests = false;

   @Property(doc = "Max duration of the test. Default is infinite.", converter = TimeConverter.class)
   public long timeout = 0;

   @Property(doc = "Delay to let all threads start executing operations. Default is 0.", converter = TimeConverter.class)
   public long rampUp = 0;

   @Property(doc = "Whether an error from transaction commit/rollback should be logged as error. Default is true.")
   public boolean logTransactionExceptions = true;

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
      if (totalThreads <= 0 && numThreadsPerNode <= 0)
         throw new IllegalStateException("You have to set either total-threads or num-threads-per-node.");
      if (totalThreads > 0 && numThreadsPerNode > 0)
         throw new IllegalStateException("You have to set only one ot total-threads, num-threads-per-node");
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
      List<StatisticsAck> statisticsAcks = instancesOf(acks, StatisticsAck.class);
      Statistics aggregated = statisticsAcks.stream().flatMap(ack -> ack.statistics.stream()).reduce(null, Statistics.MERGE);
      for (StatisticsAck ack : statisticsAcks) {
         if (ack.statistics != null) {
            if (test != null) {
               int testIteration = getTestIteration();
               String iterationValue = resolveIterationValue();
               if (iterationValue != null) {
                  test.setIterationValue(testIteration, iterationValue);
               }
               test.addStatistics(testIteration, ack.getSlaveIndex(), ack.statistics);
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
      List<Statistics> results = gatherResults(stressors, new StatisticsResultRetriever());
      return new StatisticsAck(slaveState, results);
   }

   protected <T> List<T> gatherResults(List<LegacyStressor> stressors, ResultRetriever<T> retriever) {
      if (mergeThreadStats) {
         return stressors.stream()
            .map(retriever::getResult)
            .reduce(retriever::merge)
            .map(Collections::singletonList).orElse(Collections.emptyList());
      } else {
         return stressors.stream()
            .map(retriever::getResult)
            .filter(r -> r != null)
            .collect(Collectors.toList());
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

      T merge(T stats1, T stats2);
   }

   protected static class StatisticsResultRetriever implements ResultRetriever<Statistics> {
      public StatisticsResultRetriever() {}

      @Override
      public Statistics getResult(LegacyStressor stressor) {
         return stressor.getStats();
      }

      @Override
      public Statistics merge(Statistics stats1, Statistics stats2) {
         return Statistics.MERGE.apply(stats1, stats2);
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
      public final List<Statistics> statistics;

      public StatisticsAck(SlaveState slaveState, List<Statistics> statistics) {
         super(slaveState);
         this.statistics = statistics;
      }
   }
}
