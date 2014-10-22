package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.radargun.DistStageAck;
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
import org.radargun.stats.IterationStatistics;
import org.radargun.stats.Statistics;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.Projections;
import org.radargun.utils.TimeConverter;
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

   @Property(name = "statistics", doc = "Type of gathered statistics. Default are the 'default' statistics " +
         "(fixed size memory footprint for each operation).", complexConverter = Statistics.Converter.class)
   protected Statistics statisticsPrototype = new DefaultStatistics();

   @Property(doc = "Property, which value will be used to identify individual iterations (e.g. num-threads).")
   protected String iterationProperty;

   @Property(doc = "If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.",
      complexConverter = PerformanceCondition.Converter.class)
   protected PerformanceCondition repeatCondition;

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
         long startNanos = System.nanoTime();
         log.info("Starting test " + testName);
         List<List<Statistics>> results = execute();
         log.info("Finished test. Test duration is: " + Utils.getNanosDurationString(System.nanoTime() - startNanos));
         return newStatisticsAck(slaveState, results);
      } catch (Exception e) {
         return errorResponse("Exception while initializing the test", e);
      }
   }

   protected StatisticsAck newStatisticsAck(SlaveState slaveState, List<List<Statistics>> iterations) {
      return new StatisticsAck(slaveState, iterations);
   }

   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Report.Test test = getTest();
      testIteration = test == null ? 0 : test.getIterations().size();
      Statistics aggregated = createStatistics();
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
                  aggregated.merge(s);
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
            if (repeatCondition.evaluate(threads, aggregated)) {
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

   protected Report.Test getTest() {
      if (testName == null || testName.isEmpty()) {
         log.warn("No test name - results are not recorded");
         return null;
      } else if (testName.equalsIgnoreCase("warmup")) {
         log.info("This test was executed as a warmup");
         return null;
      } else {
         Report report = masterState.getReport();
         return report.createTest(testName, iterationProperty, amendTest);
      }
   }

   public List<List<Statistics>> execute() {
      Completion completion;
      if (duration > 0) {
         completion = new TimeStressorCompletion(duration, requestPeriod);
      } else {
         completion = new OperationCountCompletion(numRequests, requestPeriod, logPeriod);
      }
      setCompletion(completion);

      startLatch = new CountDownLatch(1);
      int myFirstThread = getFirstThreadOn(slaveState.getSlaveIndex());
      int myNumThreads = getNumThreadsOn(slaveState.getSlaveIndex());
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
         finishLatch.await();
      } catch (InterruptedException e) {
         throw new IllegalStateException("Unexpected interruption", e);
      } finally {
         finished = true;
      }
      List<Statistics> stats = new ArrayList<Statistics>(stressors.size());
      for (Stressor stressor : stressors) {
         try {
            stressor.join();
            Statistics s = stressor.getStats();
            if (s != null) { // stressor could have crashed during initialization
               stats.add(s);
            }
         } catch (InterruptedException e) {
            throw new IllegalStateException("Unexpected interruption", e);
         }
      }

      List<List<Statistics>> all = new ArrayList<>();
      all.add(new ArrayList<Statistics>());
      /* expand the iteration statistics into iterations */
      for (Statistics s : stats) {
         if (s instanceof IterationStatistics) {
            int iteration = 0;
            for (IterationStatistics.Iteration it : ((IterationStatistics) s).getIterations()) {
               while (iteration >= all.size()) {
                  all.add(new ArrayList<Statistics>(stats.size()));
               }
               all.get(iteration++).add(it.statistics);
            }
         } else {
            all.get(0).add(s);
         }
      }
      return all;
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

      protected StatisticsAck(SlaveState slaveState, List<List<Statistics>> iterations) {
         super(slaveState);
         this.iterations = iterations;
      }
   }
}
