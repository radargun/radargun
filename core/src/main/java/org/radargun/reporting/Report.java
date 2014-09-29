package org.radargun.reporting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Definition;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Statistics;

/**
 * Data collected during scenarion on one configuration
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Report implements Comparable<Report> {
   private static final Log log = LogFactory.getLog(Report.class);

   /* Configuration part */
   private Configuration configuration;
   private Cluster cluster;
   /* Scenario */
   private List<Stage> stages = new ArrayList<>();
   /* Service configurations */
   private Map<Integer, Map<String, Properties>> normalizedServiceConfigs = new HashMap<Integer, Map<String, Properties>>();
   private Map<Integer, Map<String, byte[]>> originalServiceConfig = new HashMap<Integer, Map<String, byte[]>>();
   /* Results part */
   private List<Timeline> timelines = new ArrayList<Timeline>();
   /* Test name - iterations */
   private Map<String, Test> tests = new LinkedHashMap<String, Test>();

   public Report(Configuration configuration, Cluster cluster) {
      this.configuration = configuration;
      this.cluster = cluster;
      this.timelines.add(new Timeline(-1));
   }

   public void addNormalizedServiceConfig(int slaveIndex, Map<String, Properties> serviceConfigs) {
      normalizedServiceConfigs.put(slaveIndex, serviceConfigs);
   }

   public Map<Integer, Map<String, Properties>> getNormalizedServiceConfigs() {
      return Collections.unmodifiableMap(normalizedServiceConfigs);
   }

   public void addOriginalServiceConfig(int slaveIndex, Map<String, byte[]> serviceConfigs) {
      originalServiceConfig.put(slaveIndex, serviceConfigs);
   }

   public Map<Integer, Map<String, byte[]>> getOriginalServiceConfig() {
      return Collections.unmodifiableMap(originalServiceConfig);
   }

   public Stage addStage(String name) {
      Stage stage = new Stage(name);
      stages.add(stage);
      return stage;
   }

   public List<Stage> getStages() {
      return Collections.unmodifiableList(stages);
   }

   public void addTimelines(Collection<Timeline> timelines) {
      this.timelines.addAll(timelines);
   }

   public List<Timeline> getTimelines() {
      return Collections.unmodifiableList(timelines);
   }

   public Test createTest(String testName) {
      if (tests.containsKey(testName)) {
         throw new IllegalArgumentException("Test '" + testName + "' is already defined");
      }
      Test test = new Test(testName);
      tests.put(testName, test);
      return test;
   }

   public Test getTest(String testName) {
      return tests.get(testName);
   }

   public Report.Test getOrCreateTest(String testName, boolean preferGet) {
      Report.Test test;
      if (preferGet) {
         test = tests.get(testName);
         if (test != null) {
            return test;
         }
         log.warn("Test '" + testName + "' was not found - creating new test.");
      }
      return createTest(testName);
   }

   public Configuration getConfiguration() {
      return configuration;
   }

   public Cluster getCluster() {
      return cluster;
   }

   public Collection<Test> getTests() {
      return tests.values();
   }

   @Override
   public int compareTo(Report o) {
      int c = configuration.name.compareTo(o.configuration.name);
      return c != 0 ? c : cluster.compareTo(o.cluster);
   }

   public class Test {
      public final String name;
      private ArrayList<TestIteration> iterations = new ArrayList<TestIteration>();

      public Test(String name) {
         this.name = name;
      }

      public void addStatistics(int iteration, int slaveIndex, List<Statistics> stats) {
         addStatistics(iteration, slaveIndex, stats, null, null);
      }

      public void addStatistics(int iteration, int slaveIndex, List<Statistics> stats, String iterationName, String iterationValue) {
         ensureIterations(iteration + 1);
         checkIteration(iteration, iterationName, iterationValue);
         iterations.get(iteration).addStatistics(slaveIndex, stats, iterationName, iterationValue);
      }

      public void addResult(int iteration, Map<String, TestResult> results) {
         ensureIterations(iteration + 1);
         iterations.get(iteration).setResults(results);
      }

      private void ensureIterations(int size) {
         iterations.ensureCapacity(size);
         for (int i = iterations.size(); i < size; ++i) iterations.add(new TestIteration());
      }

      private void checkIteration(int iteration, String iterationName, String iterationValue) {
         TestIteration ti = iterations.get(iteration);
         ti.iterationName = iterationName;
         ti.iterationValue = iterationValue;
      }

      public List<TestIteration> getIterations() {
         return Collections.unmodifiableList(iterations);
      }

      public Report getReport() {
         return Report.this;
      }
   }

   public static class TestIteration {
      /* Slave index - Statistics from threads */
      private Map<Integer, List<Statistics>> statistics = new HashMap<Integer, List<Statistics>>();
      private Map<String, TestResult> results;

      private int threadCount;
      private String iterationName;
      private String iterationValue;

      public void addStatistics(int slaveIndex, List<Statistics> slaveStats, String iterationName, String iterationValue) {
         statistics.put(slaveIndex, slaveStats);
         threadCount += slaveStats.size();
         this.iterationName = iterationName;
         this.iterationValue = iterationValue;
      }

      public void setResults(Map<String, TestResult> results) {
         if (this.results != null) throw new IllegalStateException("Results already set: " + this.results);
         this.results = results;
      }

      public Set<Map.Entry<Integer, List<Statistics>>> getStatistics() {
         return Collections.unmodifiableSet(statistics.entrySet());
      }

      public int getThreadCount() {
         return threadCount;
      }

      public Map<String, TestResult> getResults() {
         return results == null ? null : Collections.unmodifiableMap(results);
      }

      public String getIterationName() {
         return iterationName;
      }

      public String getIterationValue() {
         return iterationValue;
      }
   }

   /**
    * Other data of the test that should be reported but don't contain Operation execution times.
    */
   public static class TestResult {
      public final Map<Integer, SlaveResult> slaveResults;
      public final String aggregatedValue;
      public final boolean suspicious;
      public final String iterationName;
      public final String iterationValue;

      public TestResult(Map<Integer, SlaveResult> slaveResults, String aggregatedValue, boolean suspicious, String iterationName, String iterationValue) {
         this.slaveResults = Collections.unmodifiableMap(slaveResults);
         this.aggregatedValue = aggregatedValue;
         this.suspicious = suspicious;
         this.iterationName = iterationName;
         this.iterationValue = iterationValue;
      }
   }

   /**
    * Result data from single slave.
    */
   public static class SlaveResult {
      public final String value;
      public final boolean suspicious;

      public SlaveResult(String value, boolean suspicious) {
         this.value = value;
         this.suspicious = suspicious;
      }
   }

   public static class Stage {
      private final String name;
      private final List<Property> properties = new ArrayList<>();

      public Stage(String name) {
         this.name = name;
      }

      public String getName() {
         return name;
      }

      public void addProperty(String name, Definition definition, Object value) {
         properties.add(new Property(name, definition, value));
      }

      public List<Property> getProperties() {
         return Collections.unmodifiableList(properties);
      }
   }

   public static class Property {
      private final String name;
      private final Definition definition;
      private final Object value;

      public Property(String name, Definition definition, Object value) {
         this.name = name;
         this.definition = definition;
         this.value = value;
      }

      public String getName() {
         return name;
      }

      public Definition getDefinition() {
         return definition;
      }

      public Object getValue() {
         return value;
      }
   }
}
