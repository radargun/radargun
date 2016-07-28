package org.radargun.stages.test;

import java.util.Map;

import org.radargun.config.Init;
import org.radargun.config.Path;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stats.BasicStatistics;
import org.radargun.stats.Statistics;
import org.radargun.utils.TimeConverter;

@Stage(doc = "Base for tests.")
public abstract class BaseTestStage extends AbstractDistStage {
   @Property(doc = "Name of the test as used for reporting. Default is 'Test'.")
   public String testName = "Test";

   @Property(doc = "By default, each stage creates a new test. If this property is set to true," +
      "results are amended to existing test (as iterations). Default is false.")
   public boolean amendTest = false;

   @Property(converter = TimeConverter.class, doc = "Benchmark duration.", optional = false)
   public long duration;

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
      if (duration <= 0) {
         throw new IllegalArgumentException("Test duration must be positive.");
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
}
