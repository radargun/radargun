package org.radargun.stages.cache.stresstest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.radargun.DistStageAck;
import org.radargun.config.Init;
import org.radargun.config.Path;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.cache.generators.WrappedArrayValueGenerator;
import org.radargun.stages.helpers.BucketPolicy;
import org.radargun.state.SlaveState;
import org.radargun.stats.AllRecordingOperationStats;
import org.radargun.stats.DefaultOperationStats;
import org.radargun.stats.DefaultStatistics;
import org.radargun.stats.HistogramOperationStats;
import org.radargun.stats.HistogramStatistics;
import org.radargun.stats.MultiOperationStats;
import org.radargun.stats.OperationStats;
import org.radargun.stats.PeriodicStatistics;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.Histogram;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.Fuzzy;
import org.radargun.utils.Projections;
import org.radargun.utils.SizeConverter;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.Utils;

/**
 * Simulates the work with a distributed web sessions.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Benchmark where several client threads access cache limited by time or number of requests.",
      deprecatedName = "WebSessionBenchmark")
public class StressTestStage extends AbstractDistStage {

   @Property(doc = "Name of the test as used for reporting. Default is StressTest.")
   protected String testName = "StressTest";

   @Property(doc = "By default, each stage creates a new test. If this property is set to true," +
         "results are amended to existing test (as iterations). Default is false.")
   protected boolean amendTest = false;

   @Property(doc = "Number of operations after which a log entry should be written. Default is 10000.")
   protected int logPeriod = 10000;

   @Property(doc = "Total number of request to be made against this session: reads + writes. If duration " +
         "is specified this value is ignored. Default is 50000.")
   protected long numRequests = 50000;

   @Property(doc = "Number of key-value entries per each client thread which should be used. Default is 100.")
   protected int numEntries = 100;

   @Property(doc = "Applicable only with fixedKeys=false, makes sense for entrySize with multiple values. " +
         "Replaces numEntries; requested number of bytes in values set by the stressor. By default not set.", converter = SizeConverter.class)
   protected long numBytes = 0;

   @Property(doc = "Size of the value in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   protected Fuzzy<Integer> entrySize = Fuzzy.always(1000);

   @Property(doc = "Ratio of writes = PUT requests (percentage). Default is 20%")
   protected int writePercentage = 20;

   @Property(doc = "The frequency of removes (percentage). Default is 0%")
   protected int removePercentage = 0;

   @Property(doc = "In case we test replace performance, the frequency of replaces that should fail (percentage). Default is 40%")
   protected int replaceInvalidPercentage = 40;

   @Property(doc = "Used only when useConditionalOperations=true: The frequency of conditional removes that should fail (percentage). Default is 10%")
   protected int removeInvalidPercentage = 10;

   @Property(doc = "The number of threads that will work on this slave. Default is 10.")
   protected int numThreads = 10;

   @Property(doc = "Full class name of the key generator. Default is org.radargun.stressors.StringKeyGenerator.")
   protected String keyGeneratorClass = StringKeyGenerator.class.getName();

   @Property(doc = "Used to initialize the key generator. Null by default.")
   protected String keyGeneratorParam = null;

   @Property(doc = "Full class name of the value generator. Default is org.radargun.stressors.ByteArrayValueGenerator if useConditionalOperations=false and org.radargun.stressors.WrappedArrayValueGenerator otherwise.")
   protected String valueGeneratorClass = null;

   @Property(doc = "Used to initialize the value generator. Null by default.")
   protected String valueGeneratorParam = null;

   @Property(doc = "Specifies if the requests should be explicitely wrapped in transactions. By default" +
         "the cachewrapper is queried whether it does support the transactions, if it does," +
         "transactions are used, otherwise these are not.")
   protected Boolean useTransactions = null;

   @Property(doc = "Specifies whether the transactions should be committed (true) or rolled back (false). " +
         "Default is true")
   protected boolean commitTransactions = true;

   @Property(doc = "Number of requests in one transaction. Default is 1.")
   protected int transactionSize = 1;

   @Property(doc = "Number of keys inserted/retrieved within one operation. Applicable only when the cache wrapper" +
         "supports bulk operations. Default is 1 (no bulk operations).")
   protected int bulkSize = 1;

   @Property(doc = "When executing bulk operations, prefer version with multiple async operations over native implementation. Default is false.")
   protected boolean preferAsyncOperations = false;

   @Property(converter = TimeConverter.class, doc = "Benchmark duration. This takes precedence over numRequests. By default switched off.")
   protected long duration = 0;

   @Property(converter = TimeConverter.class, doc = "Target period of requests - e.g. when this is set to 10 ms" +
         "the benchmark will try to do one request every 10 ms. By default the requests are executed at maximum speed.")
   protected long requestPeriod = 0;

   @Property(doc = "By default each client thread operates on his private set of keys. Setting this to true " +
         "introduces contention between the threads, the numThreads property says total amount of entries that are " +
         "used by all threads. Default is false.")
   protected boolean sharedKeys = false;

   @Property(doc = "Which buckets will the stressors use. Available is 'none' (no buckets = null)," +
         "'thread' (each thread will use bucked_/threadId/) or " +
         "'all:/bucketName/' (all threads will use bucketName). Default is 'none'.",
         converter = BucketPolicy.Converter.class)
   protected BucketPolicy bucketPolicy = new BucketPolicy(BucketPolicy.Type.NONE, null);

   @Property(doc = "This option is valid only for sharedKeys=true. It forces local loading of all keys (not only numEntries/numNodes). Default is false.")
   protected boolean loadAllKeys = false;

   @Property(doc = "The keys can be fixed for the whole test run period or we the set can change over time. Default is true = fixed.")
   protected boolean fixedKeys = true;

   @Property(doc = "Due to configuration (eviction, expiration), some keys may spuriously disappear. Do not issue a warning for this situation. Default is false.")
   protected boolean expectLostKeys = false;

   @Property(doc = "If true, putIfAbsent and replace operations are used. Default is false.")
   protected boolean useConditionalOperations = false;

   @Property(doc = "Keep all keys in a pool - do not generate the keys for each request anew. Default is true.")
   protected boolean poolKeys = true;

   @Property(doc = "Generate a range for histogram with operations statistics (for use in next stress tests). Default is false.")
   protected boolean generateHistogramRange = false;

   @Property(doc = "The test will produce operation statistics in histogram. Default is false.")
   protected boolean useHistogramStatistics = false;

   @Property(doc = "The test will produce operation statistics as average values. Default is true.")
   protected boolean useSimpleStatistics = true;

   @Property(doc = "Period of single statistics result. By default periodic statistics are not used.", converter = TimeConverter.class)
   protected long statisticsPeriod = 0;

   @Property(doc = "With fixedKeys=false, maximum lifespan of an entry. Default is 1 hour.", converter = TimeConverter.class)
   protected long entryLifespan = 3600000;

   @Property(doc = "Seed used for initialization of random generators. Each thread adds its index to the seed value. By default the seed is not set.")
   protected Long seed;

   @Property(doc = "During loading phase, if the insert fails, try it again. This is the maximum number of attempts. Default is 10.")
   protected int maxLoadAttempts = 10;

   @Property(doc = "Property, which value will be used to identify individual iterations (e.g. num-threads).")
   protected String iterationProperty;

   @InjectTrait
   protected BasicOperations basicOperations;
   @InjectTrait
   protected ConditionalOperations conditionalOperations;
   @InjectTrait
   protected BulkOperations bulkOperations;
   @InjectTrait
   protected Transactional transactional;

   protected transient volatile KeyGenerator keyGenerator;
   protected transient volatile ValueGenerator valueGenerator;

   private transient ArrayList<Object> sharedKeysPool = new ArrayList<Object>();
   protected transient volatile long startNanos;
   private transient PhaseSynchronizer synchronizer = new PhaseSynchronizer();
   private transient volatile Completion completion;
   private transient volatile boolean finished = false;
   private transient volatile boolean terminated = false;
   private int testIteration; // first iteration we should use for setting the statistics

   protected transient List<Stressor> stressors = new ArrayList<Stressor>(numThreads);
   private transient Statistics statisticsPrototype = new DefaultStatistics(new DefaultOperationStats());

   @Init
   public void init() {
      if (valueGeneratorClass == null) {
         if (useConditionalOperations) valueGeneratorClass = WrappedArrayValueGenerator.class.getName();
         else valueGeneratorClass = ByteArrayValueGenerator.class.getName();
      }
   }

   protected List<List<Statistics>> execute() {
      log.info("Starting " + toString());
      loadStatistics();
      slaveState.put(BucketPolicy.LAST_BUCKET, bucketPolicy.getBucketName(-1));
      slaveState.put(KeyGenerator.KEY_GENERATOR, getKeyGenerator());
      slaveState.put(ValueGenerator.VALUE_GENERATOR, getValueGenerator());
      List<List<Statistics>> results = stress();
      storeStatistics(results);
      return results;
   }

   protected void loadStatistics() {
      Statistics statistics;
      if (generateHistogramRange) {
         statistics = new DefaultStatistics(new AllRecordingOperationStats());
      } else if (useHistogramStatistics) {
         Histogram[] histograms = (Histogram[]) slaveState.get(Histogram.OPERATIONS_HISTOGRAMS);
         if (histograms == null) {
            throw new IllegalStateException("The histogram statistics are not generated. Please run StressTestWarmup with generateHistogramRange=true");
         }
         OperationStats[] histogramProtypes = new OperationStats[histograms.length];
         for (int i = 0; i < histograms.length; ++i) {
            if (useSimpleStatistics) {
               histogramProtypes[i] = new MultiOperationStats(new DefaultOperationStats(), new HistogramOperationStats(histograms[i]));
            } else {
               histogramProtypes[i] = new HistogramOperationStats(histograms[i]);
            }
         }
         statistics = new HistogramStatistics(histogramProtypes, new DefaultOperationStats());
      } else {
         statistics = new DefaultStatistics(new DefaultOperationStats());
      }
      if (statisticsPeriod > 0) {
         statistics = new PeriodicStatistics(statistics, statisticsPeriod);
      }
      statisticsPrototype = statistics;
   }

   private void storeStatistics(List<List<Statistics>> results) {
      if (generateHistogramRange) {
         Statistics statistics = statisticsPrototype.copy();
         for (List<Statistics> iteration : results) {
            for (Statistics s : iteration) {
               s.merge(statistics);
            }
         }
         slaveState.put(Histogram.OPERATIONS_HISTOGRAMS, statistics.getRepresentations(Histogram.class));
      }
   }

   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         log.info("The stage should not run on this slave");
         return successfulResponse();
      }
      if (!isServiceRunnning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }

      log.info("Executing: " + this.toString());
      startNanos = System.nanoTime();

      try {
         List<List<Statistics>> results = execute();
         return newStatisticsAck(slaveState, results, iterationProperty, resolveIterationValue());
      } catch (Exception e) {
         return errorResponse("Exception while initializing the test", e);
      }
   }

   protected StatisticsAck newStatisticsAck(SlaveState slaveState, List<List<Statistics>> iterations, String iterationName, String iterationValue) {
      return new StatisticsAck(slaveState, iterations, iterationName, iterationValue);
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      if (!super.processAckOnMaster(acks)) return false;
      if (testName == null || testName.isEmpty()) {
         log.info("No test name - results are not recorded");
         return true;
      }
      Report report = masterState.getReport();
      Report.Test test = report.getOrCreateTest(testName, amendTest);
      testIteration = test.getIterations().size();
      for (StatisticsAck ack : Projections.instancesOf(acks, StatisticsAck.class)) {
         if (ack.iterations != null) {
            int i = getTestIteration();
            for (List<Statistics> threadStats : ack.iterations) {
               test.addStatistics(++i, ack.getSlaveIndex(), threadStats, ack.iterationName, ack.iterationValue);
            }
         } else {
            log.trace("No report received from slave: " + ack.getSlaveIndex());
         }
      }
      return true;
   }

   protected boolean defaultProcessAck(List<DistStageAck> acks) {
      return super.processAckOnMaster(acks);
   }

   public List<List<Statistics>> stress() {
      Completion completion;
      if (duration > 0) {
         completion = new TimeStressorCompletion(duration, requestPeriod);
      } else {
         completion = new OperationCountCompletion(numRequests, requestPeriod, logPeriod);
      }
      setCompletion(completion);

      if (!startOperations()) return Collections.EMPTY_LIST;
      try {
         executeOperations();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      log.info("Finished test. Test duration is: " + Utils.getNanosDurationString(System.nanoTime() - startNanos));

      List<Statistics> results = gatherResults();
      finishOperations();
      if (statisticsPeriod > 0) {
         /* expand the periodic statistics into iterations */
         List<List<Statistics>> all = new ArrayList<List<Statistics>>();
         for (Statistics s : results) {
            int iteration = 0;
            for (Statistics s2 : ((PeriodicStatistics) s).asList()) {
               while (iteration >= all.size()) {
                  all.add(new ArrayList<Statistics>(results.size()));
               }
               all.get(iteration++).add(s2);
            }
         }
         return all;
      }
      return Collections.singletonList(results);
   }

   protected boolean startOperations() {
      try {
         synchronizer.masterPhaseStart();
      } catch (InterruptedException e) {
         return false;
      }
      return true;
   }

   protected List<Statistics> gatherResults() {
      List<Statistics> stats = new ArrayList<Statistics>(stressors.size());
      for (Stressor stressor : stressors) {
         stats.add(stressor.getStats());
      }
      return stats;
   }

   protected Statistics createStatistics() {
      return statisticsPrototype.copy();
   }

   protected void executeOperations() throws InterruptedException {
      synchronizer.setSlaveCount(numThreads);
      for (int threadIndex = stressors.size(); threadIndex < numThreads; threadIndex++) {
         Stressor stressor = createStressor(threadIndex);
         stressors.add(stressor);
         stressor.start();
      }
      synchronizer.masterPhaseEnd();
      // wait until all slaves have initialized keys
      synchronizer.masterPhaseStart();
      // nothing to do here
      synchronizer.masterPhaseEnd();
      log.info("Started " + stressors.size() + " stressor threads.");
      // wait until all threads have finished
      synchronizer.masterPhaseStart();
   }

   protected Stressor createStressor(int threadIndex) {
      return new Stressor(this, getLogic(), threadIndex, slaveState.getSlaveIndex(), slaveState.getClusterSize());
   }

   protected void finishOperations() {
      finished = true;
      synchronizer.masterPhaseEnd();
      for (Stressor s : stressors) {
         try {
            s.join();
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
      }
      stressors.clear();
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

   public PhaseSynchronizer getSynchronizer() {
      return synchronizer;
   }

   public OperationLogic getLogic() {
      if (fixedKeys && numBytes > 0) {
         throw new IllegalArgumentException("numBytes can be set only for fixedKeys=false");
      } else if (sharedKeys && !fixedKeys) {
         throw new IllegalArgumentException("Cannot use both shared and non-fixed keys - not implemented");
      } else if (!fixedKeys) {
         if (!poolKeys) {
            throw new IllegalArgumentException("Keys have to be pooled with changing set.");
         }
         if (bulkSize != 1 || useConditionalOperations) {
            throw new IllegalArgumentException("Replace/bulk operations on changing set not supported.");
         }
         if (removePercentage > 0) {
            throw new IllegalArgumentException("Removes cannot be configured in when using non-fixed keys");
         }
         return new ChangingSetOperationLogic(this);
      } else if (bulkSize != 1) {
         if (bulkSize > 1 && bulkSize <= numEntries) {
            if (bulkOperations != null) {
               if (sharedKeys) {
                  return new BulkOperationLogic(this, new FixedSetSharedOperationLogic(this, sharedKeysPool), preferAsyncOperations);
               } else {
                  return new BulkOperationLogic(this, new FixedSetPerThreadOperationLogic(this), preferAsyncOperations);
               }
            } else {
               throw new IllegalArgumentException("Service " + slaveState.getServiceName() + " does not support bulk operations.");
            }
         } else {
            throw new IllegalArgumentException("Invalid bulk size, must be 1 < bulkSize(" + bulkSize + ") < numEntries(" + numEntries + ")");
         }
      } else if (useConditionalOperations) {
         if (sharedKeys) {
            throw new IllegalArgumentException("Atomics on shared keys are not supported.");
         } else if (conditionalOperations != null) {
            if (!poolKeys) {
               log.warn("Keys are not pooled, but last values must be recorded!");
            }
            return new FixedSetConditionalOperationLogic(this);
         } else {
            throw new IllegalArgumentException("Atomics can be executed only on wrapper which supports atomic operations.");
         }
      } else if (sharedKeys) {
         return new FixedSetSharedOperationLogic(this, sharedKeysPool);
      } else {
         return new FixedSetPerThreadOperationLogic(this);
      }
   }

   protected Object generateValue(Object key, int maxValueSize, Random random) {
      int size = entrySize.next(random);
      size = Math.min(size, maxValueSize);
      return getValueGenerator().generateValue(key, size, random);
   }

   public KeyGenerator getKeyGenerator() {
      if (keyGenerator == null) {
         synchronized (this) {
            if (keyGenerator != null) return keyGenerator;
            log.info("Using key generator " + keyGeneratorClass + ", param " + keyGeneratorParam);
            keyGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), keyGeneratorClass, keyGeneratorParam);
         }
      }
      return keyGenerator;
   }

   public ValueGenerator getValueGenerator() {
      if (valueGenerator == null) {
         synchronized (this) {
            if (valueGenerator != null) return valueGenerator;
            log.info("Using value generator " + valueGeneratorClass + ", param " + valueGeneratorParam);
            valueGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), valueGeneratorClass, valueGeneratorParam);
         }
      }
      return valueGenerator;
   }

   protected static void avoidJit(Object result) {
      //this line was added just to make sure JIT doesn't skip call to cacheWrapper.get
      if (result != null && System.identityHashCode(result) == result.hashCode()) System.out.print("");
   }

   public Long getSeed() {
      return seed;
   }

   protected int getTestIteration() {
      return testIteration;
   }

   protected String resolveIterationValue() {
      if (iterationProperty != null) {
         Map<String, Path> properties = PropertyHelper.getProperties(getClass(), true, false);
         String propertyString = PropertyHelper.getPropertyString(properties.get(iterationProperty), this);
         if (propertyString == null) {
            throw new IllegalStateException("Unable to resolve iteration property '" + iterationProperty + "'.");
         }
         return propertyString;
      }
      return null;
   }

   protected static class StatisticsAck extends DistStageAck {
      private final List<List<Statistics>> iterations;
      private String iterationName;
      private String iterationValue;

      protected StatisticsAck(SlaveState slaveState, List<List<Statistics>> iterations, String iterationName, String iterationValue) {
         super(slaveState);
         this.iterations = iterations;
         this.iterationName = iterationName;
         this.iterationValue = iterationValue;
      }
   }
}
