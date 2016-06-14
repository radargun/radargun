package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.Operation;
import org.radargun.StageResult;
import org.radargun.config.Init;
import org.radargun.config.Path;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.stats.DefaultStatistics;
import org.radargun.reporting.IterationData;
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
public abstract class TestStage extends AbstractDistStage {
   @Property(doc = "Name of the test as used for reporting. Default is 'Test'.")
   protected String testName = "Test";

   @Property(doc = "By default, each stage creates a new test. If this property is set to true," +
         "results are amended to existing test (as iterations). Default is false.")
   protected boolean amendTest = false;

   @Property(doc = "Number of operations after which a log entry should be written. Default is 10000.")
   protected int logPeriod = 10000;

   @Property(doc = "Total number of request to be made against this session: reads + writes. If duration " +
         "is specified this value is ignored. Default is 50000.")
   protected long numRequests = 50000;

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

   @Property(converter = TimeConverter.class, doc = "Benchmark duration. This takes precedence over numRequests. By default switched off.")
   protected long duration = 0;

   @Property(converter = TimeConverter.class, doc = "Target period of requests - e.g. when this is set to 10 ms" +
         "the benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.")
   protected long requestPeriod = 0;

   @Property(doc = "Local threads synchronize on starting each round of requests. Note that with requestPeriod > 0, " +
         "there is still the random ramp-up delay. Default is false.")
   protected boolean synchronousRequests = false;

   @Property(doc = "Max duration of the test. Default is infinite.", converter = TimeConverter.class)
   protected long timeout = 0;

   @Property(name = "statistics", doc = "Type of gathered statistics. Default are the 'default' statistics " +
         "(fixed size memory footprint for each operation).", complexConverter = Statistics.Converter.class)
   protected Statistics statisticsPrototype = new DefaultStatistics();

   @Property(doc = "Property, which value will be used to identify individual iterations (e.g. num-threads).")
   protected String iterationProperty;

   @Property(doc = "If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.",
      complexConverter = PerformanceCondition.Converter.class)
   protected PerformanceCondition repeatCondition;

   @Property(doc = "Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.")
   protected boolean mergeThreadStats = false;

   @InjectTrait
   protected Transactional transactional;

   protected CountDownLatch startLatch;
   protected CountDownLatch finishLatch;
   protected volatile Completion completion;
   protected volatile boolean finished = false;
   protected volatile boolean terminated = false;
   protected int testIteration; // first iteration we should use for setting the statistics

   @Init
   public void init() {
      if (totalThreads <= 0 && numThreadsPerNode <= 0) throw new IllegalStateException("You have to set either total-threads or num-threads-per-node.");
      if (totalThreads > 0 && numThreadsPerNode > 0) throw new IllegalStateException("You have to set only one ot total-threads, num-threads-per-node");
      if (totalThreads < 0 || numThreadsPerNode < 0) throw new IllegalStateException("Number of threads can't be < 0");
   }

   protected static void avoidJit(Object result) {
      //this line was added just to make sure JIT doesn't skip call to cacheWrapper.get
      if (result != null && System.identityHashCode(result) == result.hashCode()) System.out.print("");
   }

   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }

      try {
         long startNanos = TimeService.nanoTime();
         log.info("Starting test " + testName);
         List<Stressor> stressors = execute();
         log.info("Finished test. Test duration is: " + Utils.getNanosDurationString(TimeService.nanoTime() - startNanos));
         return newStatisticsAck(stressors);
      } catch (Exception e) {
         return errorResponse("Exception while initializing the test", e);
      }
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
                  if (test.getGroupOperationsMap() == null) {
                     test.setGroupOperationsMap(ack.getGroupOperationsMap());
                  }
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
      if (repeatCondition == null) {
         return StageResult.SUCCESS;
      } else {
         try {
            if (repeatCondition.evaluate(aggregated)) {
               log.info("Loop-condition condition was satisfied, continuing the loop.");
               return StageResult.SUCCESS;
            } else {
               log.info("Loop-condition condition not satisfied, terminating the loop");
               return StageResult.BREAK;
            }
         } catch (Exception e) {
            log.info("Loop-condition has thrown exception, terminating the loop", e);
            return StageResult.BREAK;
         }
      }
   }

   protected Report.Test getTest(boolean allowExisting) {
      if (testName == null || testName.isEmpty()) {
         log.warn("No test name - results are not recorded");
         return null;
      } else if (testName.equalsIgnoreCase("warmup")) {
         log.info("This test was executed as a warmup");
         return null;
      } else {
         Report report = masterState.getReport();
         return report.createTest(testName, iterationProperty, allowExisting);
      }
   }

   public List<Stressor> execute() {
      long startTime = TimeService.currentTimeMillis();
      int myFirstThread = getFirstThreadOn(slaveState.getSlaveIndex());
      int myNumThreads = getNumThreadsOn(slaveState.getSlaveIndex());

      Completion completion;
      if (duration > 0) {
         completion = new TimeStressorCompletion(duration, requestPeriod);
      } else {
         completion = new OperationCountCompletion(numRequests, requestPeriod, logPeriod);
      }
      if (synchronousRequests) {
         completion = new SynchronousCompletion(completion, myNumThreads);
      }
      setCompletion(completion);

      startLatch = new CountDownLatch(1);
      finishLatch = new CountDownLatch(myNumThreads);
      List<Stressor> stressors = new ArrayList<>();
      for (int threadIndex = stressors.size(); threadIndex < myNumThreads; threadIndex++) {
         Stressor stressor = new Stressor(this, getLogic(), myFirstThread + threadIndex, threadIndex);
         stressors.add(stressor);
         stressor.start();
      }
      log.info("Started " + stressors.size() + " stressor threads.");
      startLatch.countDown();
      try {
         if (timeout > 0) {
            long waitTime = getWaitTime(startTime);
            if (waitTime <= 0 || !finishLatch.await(waitTime, TimeUnit.MILLISECONDS)) {
               throw new TestTimeoutException();
            }
         } else {
            finishLatch.await();
         }
      } catch (InterruptedException e) {
         throw new IllegalStateException("Unexpected interruption", e);
      } finally {
         finished = true;
      }
      for (Stressor stressor : stressors) {
         try {
            if (timeout > 0) {
               long waitTime = getWaitTime(startTime);
               if (waitTime <= 0) throw new TestTimeoutException();
               stressor.join(waitTime);
            } else {
               stressor.join();
            }
         } catch (InterruptedException e) {
            throw new TestTimeoutException(e);
         }
      }
      return stressors;
   }

   protected DistStageAck newStatisticsAck(List<Stressor> stressors) {
      List<List<Statistics>> results = gatherResults(stressors, new StatisticsResultRetriever());
      return new StatisticsAck(slaveState, results, statisticsPrototype.getGroupOperationsMap());
   }

   protected <T> List<List<T>> gatherResults(List<Stressor> stressors, ResultRetriever<T> retriever) {
      List<T> results = new ArrayList<>(stressors.size());
      for (Stressor stressor : stressors) {
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

   protected String resolveIterationValue() {
      if (iterationProperty != null) {
         Map<String, Path> properties = PropertyHelper.getProperties(getClass(), true, false, true);
         String propertyString = PropertyHelper.getPropertyString(properties.get(iterationProperty), this);
         if (propertyString == null) {
            throw new IllegalStateException("Unable to resolve iteration property '" + iterationProperty + "'.");
         }
         return propertyString;
      }
      return null;
   }

   protected Statistics createStatistics() {
      return statisticsPrototype.copy();
   }

   protected boolean isFinished() {
      return finished;
   }

   protected boolean isTerminated() {
      return terminated;
   }

   public void setTerminated() {
      this.terminated = true;
   }

   protected void setCompletion(Completion completion) {
      this.completion = completion;
   }

   public Completion getCompletion() {
      return completion;
   }

   public CountDownLatch getStartLatch() {
      return startLatch;
   }

   public CountDownLatch getFinishLatch() {
      return finishLatch;
   }

   public boolean useTransactions(String cacheName) {
      return useTransactions.use(transactional, cacheName, transactionSize);
   }

   public abstract OperationLogic getLogic();

   protected int getTestIteration() {
      return testIteration;
   }

   protected static class StatisticsAck extends DistStageAck {
      private final List<List<Statistics>> iterations;
      private final Map<String, Set<Operation>> groupOperationsMap;

      protected StatisticsAck(SlaveState slaveState, List<List<Statistics>> iterations, Map<String, Set<Operation>> groupOperationsMap) {
         super(slaveState);
         this.iterations = iterations;
         this.groupOperationsMap = groupOperationsMap;
      }

      public Map<String, Set<Operation>> getGroupOperationsMap() {
         return groupOperationsMap;
      }
   }

   protected interface ResultRetriever<T> {
      T getResult(Stressor stressor);
      void mergeResult(T into, T that);
   }

   protected static class StatisticsResultRetriever implements ResultRetriever<Statistics> {
      public StatisticsResultRetriever() {}

      @Override
      public Statistics getResult(Stressor stressor) {
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
}
