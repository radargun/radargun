package org.radargun.stages.iteration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import org.radargun.stats.DefaultOperationStats;
import org.radargun.stats.DefaultStatistics;
import org.radargun.stats.Statistics;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Iterable;
import org.radargun.utils.Projections;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
// TODO: rewrite to inherit from TestStage
@Stage(doc = "Iterates through all entries.")
public class IterateStage extends AbstractDistStage {

   @Property(doc = "Name of this test. Default is 'IterationTest'.")
   protected String testName = "IterationTest";

   @Property(doc = "By default, each stage creates a new test. If this property is set to true, " +
         "results are amended to existing test (as iterations). Default is false.")
   protected boolean amendTest = false;

   @Property(doc = "Full class name of the filter used to iterate through entries. Default is none (accept all).")
   protected String filterClass;

   @Property(doc = "Parameters for the filter (used to resolve its properties). No defaults.")
   protected String filterParam;

   @Property(doc = "Full class name of the converter. Default is no converter (Map.Entry<K, V> is returned).")
   protected String converterClass;

   @Property(doc = "Parameter for the converter (used to resolve its properties). No defaults.")
   protected String converterParam;

   @Property(doc = "Name of the container (e.g. cache, DB table etc.) that should be iterated. Default is the default container.")
   protected String containerName;

   @Property(doc = "Number of stressor threads that should iterate the cache in parallel. Default is 10.")
   protected int numThreads = 10;

   @Property(doc = "Max duration of the iteration. Default is infinite.", converter = TimeConverter.class)
   protected long timeout = 0;

   @Property(doc = "Number of iterations that should be executed. Default is 100.")
   protected int numLoops = 100;

   @Property(doc = "Number of next() calls that are allowed to fail until we break the loop. Default is 100.")
   protected int maxNextFailures = 100;

   @Property(doc = "Fail the stage if some of the stressors has failed. Default is true.")
   protected boolean failOnFailedIteration = true;

   @Property(doc = "Fail when the number of elements iterated is not same. Default is true.")
   protected boolean failOnUnevenElements = true;

   @Property(doc = "Fail when the number of elements is different than total size. Default is true if filter is not defined and false otherwise.")
   protected Boolean failOnNotTotalSize;

   @Property(doc = "Property, which value will be used to identify individual iterations (e.g. num-threads).")
   protected String iterationProperty;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected Iterable iterable;

   @InjectTrait
   protected CacheInformation info;

   @Init
   public void init() {
      failOnNotTotalSize = (filterClass == null);
   }

   @Override
   public DistStageAck executeOnSlave() {
      // TODO: customize stats
      Statistics stats = new DefaultStatistics(new DefaultOperationStats());
      CountDownLatch startLatch = new CountDownLatch(1);
      ArrayList<IteratingStressor> stressors = new ArrayList<IteratingStressor>(numThreads);
      try {
         for (int i = 0; i < numThreads; ++i) {
            Iterable.Filter filter = null;
            Iterable.Converter converter = null;
            try {
               if (filterClass != null) {
                  filter = Utils.instantiateAndInit(slaveState.getClassLoader(), filterClass, filterParam);
               }
               if (converterClass != null) {
                  converter = Utils.instantiateAndInit(slaveState.getClassLoader(), converterClass, converterParam);
               }
            } catch (Exception e) {
               terminate(stressors);
               return errorResponse("Failed to create the filter or converter", e);
            }
            IteratingStressor stressor = new IteratingStressor(i, iterable, containerName, filter, converter,
                  maxNextFailures, numLoops, startLatch, stats.newInstance());
            stressors.add(stressor);
            stressor.start();
         }
      } finally {
         startLatch.countDown();
      }
      long testStart = System.currentTimeMillis();
      int joined = 0;
      outer_loop: while (joined < numThreads) {
         for (IteratingStressor stressor : stressors) {
            long waitTime;
            if (timeout > 0) {
               waitTime = testStart + timeout - System.currentTimeMillis();
               if (waitTime <= 0) {
                  log.warn("Timed out waiting for threads.");
                  terminate(stressors);
                  break outer_loop;
               }
            } else {
               waitTime = 0;
            }
            try {
               stressor.join(waitTime);
               ++joined;
            } catch (InterruptedException e) {
               terminate(stressors);
               return errorResponse("Interrupted when waiting for the stressors", e);
            }
         }
      }
      ArrayList<StressorResult> results = new ArrayList<StressorResult>(numThreads);
      for (IteratingStressor stressor : stressors) {
         results.add(new StressorResult(stressor.getStats(), stressor.getMinElements(), stressor.getMaxElements(), stressor.isFailed()));
      }
      long totalSize = -1;
      if (info != null) {
         totalSize = info.getCache(containerName).getTotalSize();
      }
      return new IterationAck(slaveState, results, totalSize);
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Report.Test test = null;
      if (testName == null || testName.isEmpty()) {
         log.warn("No test name - results are not recorded");
      } else if (testName.equalsIgnoreCase("warmup")) {
         log.info("This test was executed as a warmup");
      } else {
         test = masterState.getReport().getOrCreateTest(testName, amendTest);
      }
      long prevTotalSize = -1;
      long totalMinElements = -1, totalMaxElements = -1;
      Map<Integer, Report.SlaveResult> slaveResults = new HashMap<>();
      int testIteration = test.getIterations().size();
      for (IterationAck ack : Projections.instancesOf(acks, IterationAck.class)) {
         if (test != null) {
            test.addStatistics(testIteration, ack.getSlaveIndex(), Projections.project(ack.results, new Projections.Func<StressorResult, Statistics>() {
               @Override
               public Statistics project(StressorResult result) {
                  return result.stats;
               }
            }), iterationProperty, resolveIterationValue());
         }
         long slaveMinElements = -1, slaveMaxElements = -1;
         for (int i = 0; i < ack.results.size(); ++i) {
            StressorResult sr = ack.results.get(i);
            if (sr.failed) {
               result = failOnFailedIteration ? errorResult() : result;
               log.warnf("Slave %d, stressor %d has failed", ack.getSlaveIndex(), i);
            } else {
               if (sr.minElements != sr.maxElements) {
                  log.warnf("Slave %d, stressor %d reports %d .. %d elements",
                        ack.getSlaveIndex(), i, sr.minElements, sr.maxElements);
                  result = failOnUnevenElements ? errorResult() : result;
               }
               if (totalMinElements < 0) {
                  totalMinElements = sr.minElements;
                  totalMaxElements = sr.maxElements;
               }
               else if (totalMinElements != sr.minElements || totalMaxElements != sr.maxElements) {
                  log.warnf("Previous stressor reported %d .. %d elements but slave %d, stressor %d reports %d .. %d elements",
                        totalMinElements, totalMaxElements, ack.getSlaveIndex(), i, sr.minElements, sr.maxElements);
                  result = failOnUnevenElements ? errorResult() : result;
               }
               if (ack.totalSize >= 0 && (sr.minElements != ack.totalSize || sr.maxElements != ack.totalSize)) {
                  log.warnf("Slave %d stressor %d reports %d element but " +
                        "total size is %d", ack.getSlaveIndex(), i, sr.maxElements, ack.totalSize);
                  result = failOnNotTotalSize ? errorResult() : result;
               }
               totalMinElements = Math.min(totalMinElements, sr.minElements);
               totalMaxElements = Math.min(totalMaxElements, sr.maxElements);
               if (slaveMinElements < 0) {
                  slaveMinElements = sr.minElements;
                  slaveMaxElements = sr.maxElements;
               } else {
                  slaveMinElements = Math.min(slaveMinElements, sr.minElements);
                  slaveMaxElements = Math.min(slaveMaxElements, sr.maxElements);
               }
            }
         }
         if (prevTotalSize < 0) prevTotalSize = ack.totalSize;
         else if (prevTotalSize != ack.totalSize) {
            log.warnf("Previous total size was %d but slave %d reports total size %d", prevTotalSize, ack.getSlaveIndex(), ack.totalSize);
            result = failOnNotTotalSize ? errorResult() : result;
         }
         slaveResults.put(ack.getSlaveIndex(), new Report.SlaveResult(range(slaveMinElements, slaveMaxElements),
               slaveMinElements != slaveMaxElements));
      }
      if (test != null) {
         test.addResult(testIteration, Collections.singletonMap("Elements", new Report.TestResult(
               slaveResults, range(totalMinElements, totalMaxElements), totalMinElements != totalMaxElements,
               iterationProperty, resolveIterationValue())));
      }
      return result;
   }

   private String range(long min, long max) {
      return min == max ? String.valueOf(min) : String.format("%d .. %d", min, max);
   }

   private void terminate(Collection<IteratingStressor> stressors) {
      for (IteratingStressor stressor : stressors) {
         stressor.terminate();
      }
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         log.warn("Interrupted when terminating threads");
      }
      for (IteratingStressor stressor : stressors) {
         if (stressor.getState() != Thread.State.TERMINATED) {
            stressor.interrupt();
         }
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

   private static class IterationAck extends DistStageAck {
      private List<StressorResult> results;
      private long totalSize;

      public IterationAck(SlaveState slaveState, List<StressorResult> results, long totalSize) {
         super(slaveState);
         this.results = results;
         this.totalSize = totalSize;
      }
   }

   private static class StressorResult implements Serializable {
      private Statistics stats;
      private long minElements, maxElements;
      private boolean failed;

      private StressorResult(Statistics stats, long minElements, long maxElements, boolean failed) {
         this.stats = stats;
         this.minElements = minElements;
         this.maxElements = maxElements;
         this.failed = failed;
      }
   }
}
