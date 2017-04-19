package org.radargun.stages.test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.radargun.stats.BasicStatistics;
import org.radargun.stats.Statistics;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Base for tests.")
public abstract class BaseTestStage extends AbstractDistStage {
   @Property(doc = "Name of the test as used for reporting. Default is 'Test'.")
   public String testName = "Test";

   @Property(doc = "By default, each stage creates a new test. If this property is set to true," +
      "results are amended to existing test (as iterations). Default is false.")
   public boolean amendTest = false;

   @Property(converter = TimeConverter.class, doc = "Benchmark duration. You have to set either this or 'totalNumOperations'.")
   public long duration = 0;

   @Property(doc = "The total number of operations to perform during the test. You have to set either this or 'duration'.")
   public long numOperations = 0;

   @Property(name = "statistics", doc = "Type of gathered statistics. Default are the 'default' statistics " +
      "(fixed size memory footprint for each operation).", complexConverter = Statistics.Converter.class)
   public Statistics statisticsPrototype = new BasicStatistics();

   @Property(doc = "Property, which value will be used to identify individual iterations (e.g. num-threads).")
   public String iterationProperty;

   @Property(doc = "If this performance condition was not satisfied during this test, the current repeat will be exited. Default is none.",
      complexConverter = PerformanceCondition.Converter.class)
   public PerformanceCondition repeatCondition;

   @Property(doc = "Merge statistics from all threads on single node to one record, instead of storing them all in-memory. Default is false.")
   public boolean mergeThreadStats = false;

   protected int testIteration; // first iteration we should use for setting the statistics

   @Init
   public void check() {
      if ((duration > 0 && numOperations > 0) || (duration == 0 && numOperations == 0)) {
         throw new IllegalArgumentException("Set either the test duration or totalNumOperations.");
      }
      if (duration < 0) {
         throw new IllegalArgumentException("Test duration must be positive.");
      }
      if (numOperations < 0) {
         throw new IllegalArgumentException("Test totalNumOperations must be positive. " + numOperations);
      }
   }

   protected Report.Test getTest(boolean allowExisting) {
      return getTest(allowExisting, testName);
   }

   protected Report.Test getTest(boolean allowExisting, String testName) {
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

   protected int getTestIteration() {
      return testIteration;
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

   protected boolean checkRepeatCondition(Statistics aggregated) {
      if (repeatCondition == null) {
         return true;
      }
      try {
         if (repeatCondition.evaluate(aggregated)) {
            log.info("Loop-condition condition was satisfied, continuing the loop.");
            return true;
         } else {
            log.info("Loop-condition condition not satisfied, terminating the loop");
            return false;
         }
      } catch (Exception e) {
         log.info("Loop-condition has thrown exception, terminating the loop", e);
         return false;
      }
   }

   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      return processAckOnMaster(acks, testName);
   }

   protected StageResult processAckOnMaster(List<DistStageAck> acks, String testNameOverride) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      Report.Test test = getTest(amendTest, testNameOverride);
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
               if (test.getGroupOperationsMap() == null) {
                  test.setGroupOperationsMap(ack.groupOperationsMap);
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

   protected static class StatisticsAck extends DistStageAck {
      public final List<Statistics> statistics;
      public final Map<String, Set<Operation>> groupOperationsMap;

      public StatisticsAck(SlaveState slaveState, List<Statistics> statistics, Map<String, Set<Operation>> groupOperationsMap) {
         super(slaveState);
         this.statistics = statistics;
         this.groupOperationsMap = groupOperationsMap;
      }
   }
}
