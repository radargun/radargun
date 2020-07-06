package org.radargun.reporting;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

import org.radargun.Operation;
import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.config.Definition;
import org.radargun.stats.Statistics;

/**
 * Data collected during scenarion on one configuration
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Report implements Comparable<Report>, Serializable {

   /* Configuration part */
   private Configuration configuration;
   private Cluster cluster;
   /* Scenario */
   private List<Stage> stages = new ArrayList<>();
   /* Service configurations */
   private Map<Integer, Map<String, Properties>> normalizedServiceConfigs = new HashMap<Integer, Map<String, Properties>>();
   private Map<Integer, Map<String, byte[]>> originalServiceConfig = new HashMap<Integer, Map<String, byte[]>>();
   /* Results part */
   private List<Timeline> timelines = new ArrayList<>();
   /* Test name - iterations */
   private Map<String, Test> tests = new LinkedHashMap<>();

   public Report(Configuration configuration, Cluster cluster) {
      this.configuration = configuration;
      this.cluster = cluster;
      this.timelines.add(new Timeline(-1));
   }

   public void addNormalizedServiceConfig(int workerIndex, Map<String, Properties> serviceConfigs) {
      normalizedServiceConfigs.put(workerIndex, serviceConfigs);
   }

   public Map<Integer, Map<String, Properties>> getNormalizedServiceConfigs() {
      return Collections.unmodifiableMap(normalizedServiceConfigs);
   }

   public void addOriginalServiceConfig(int workerIndex, Map<String, byte[]> serviceConfigs) {
      originalServiceConfig.put(workerIndex, serviceConfigs);
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

   public boolean hasTimelineWithValuesOfType(Timeline.Category.Type type) {
      return timelines.stream().anyMatch(t -> t.containsValuesOfType(type));
   }

   /**
    * @param testName
    * @return Existing test or null.
    */
   public Test getTest(String testName) {
      return tests.get(testName);
   }

   /**
    * @param testName
    * @param iterationsName What is the changing property in different iterations. If set to null,
    *                       the iterations will use just {@link org.radargun.reporting.Report.TestIteration#id}
    * @param allowExisting Flag whether this method can return existing test.
    * @return New or existing test (if allowExisting = true), or IllegalArgumentException (if allowExisting = false)
    */
   public Report.Test createTest(String testName, String iterationsName, boolean allowExisting) {
      Test test = tests.get(testName);
      if (test != null) {
         if (allowExisting) {
            return test;
         } else {
            throw new IllegalArgumentException("Test '" + testName + "' is already defined");
         }
      }
      test = new Test(testName, iterationsName);
      tests.put(testName, test);
      return test;
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

   /**
    * One test can span multiple stages, and consists of one or more
    * {@link org.radargun.reporting.Report.TestIteration iterations}.
    * The results from single test should be plotted together in report.
    */
   public class Test implements Serializable {
      public final String name;
      public final String iterationsName;
      private ArrayList<TestIteration> iterations = new ArrayList<TestIteration>();
      private Map<String, Set<Operation>> groupOperationsMap;

      private Test(String name, String iterationsName) {
         this.name = name;
         this.iterationsName = iterationsName;
      }


      public void setGroupOperationsMap(Map<String, Set<Operation>> groupOperationsMap) {
         this.groupOperationsMap = groupOperationsMap;
      }

      public Map<String, Set<Operation>> getGroupOperationsMap() {
         return groupOperationsMap;
      }

      /**
       * Set 'description' of iteration - value of the property that is changing
       * through different iterations of the same test.
       *
       * @param iteration Iteration id (numbered from 0).
       * @param iterationValue String value. Cannot be null.
       */
      public void setIterationValue(int iteration, String iterationValue) {
         if (iterationValue == null) {
            throw new IllegalArgumentException("Null iteration value");
         }
         ensureIterations(iteration + 1);
         TestIteration ti = iterations.get(iteration);
         if (ti.value != null && !iterationValue.equals(ti.value)) {
            throw new IllegalStateException("Previous iteration value " + ti.value + ", now it is " + iterationValue);
         }
         ti.value = iterationValue;
      }

      /**
       * Set statistics from given worker for given iteration.
       * @param iteration
       * @param workerIndex
       * @param stats
       */
      public void addStatistics(int iteration, int workerIndex, List<Statistics> stats) {
         ensureIterations(iteration + 1);
         TestIteration ti = iterations.get(iteration);
         ti.addStatistics(workerIndex, stats);
      }

      /**
       * Add the result to given iteration. Each iteration can contain only one result with the same name.
       * @param iteration
       * @param result
       */
      public void addResult(int iteration, TestResult result) {
         ensureIterations(iteration + 1);
         iterations.get(iteration).addResult(result);
      }

      private void ensureIterations(int size) {
         iterations.ensureCapacity(size);
         for (int i = iterations.size(); i < size; ++i) iterations.add(new TestIteration(this, i));
      }

      public List<TestIteration> getIterations() {
         return Collections.unmodifiableList(iterations);
      }

      public Report getReport() {
         return Report.this;
      }
   }

   /**
    * One part of {@link org.radargun.reporting.Report.Test}. Usually, the iteration reflects
    * results from one stage but one stage can add more iterations.
    * Each iteration has ID and 'value' - this describes the value that is changing between stages.
    * This changing property is described in {@link Test#iterationsName}.
    */
   public static class TestIteration implements Serializable {
      public final Test test;
      public final int id;
      private String value;

      /* Worker index - Statistics from threads */
      private Map<Integer, List<Statistics>> statistics = new HashMap<>();
      private Map<String, TestResult> results = new TreeMap<>();
      private int threadCount;

      public TestIteration(Test test, int id) {
         this.test = test;
         this.id = id;
      }

      /**
       * Add statistics for given worker.
       * @param workerIndex
       * @param workerStats
       */
      public void addStatistics(int workerIndex, List<Statistics> workerStats) {
         statistics.put(workerIndex, workerStats);
         threadCount += workerStats.size();
      }

      /**
       * Add the result. The name must be unique in this iteration.
       * @param result
       */
      public void addResult(TestResult result) {
         if (results.containsKey(result.name)) {
            throw new IllegalStateException("Result '" + result.name + "' already set: " + this.results.get(result.name));
         } else {
            result.setIteration(this);
            this.results.put(result.name, result);
         }
      }

      public Set<Map.Entry<Integer, List<Statistics>>> getStatistics() {
         return Collections.unmodifiableSet(statistics.entrySet());
      }

      public List<Statistics> getStatistics(int workerIndex) {
         return statistics.get(workerIndex);
      }

      public int getThreadCount() {
         return threadCount;
      }

      public Map<String, TestResult> getResults() {
         return results == null ? null : Collections.unmodifiableMap(results);
      }

      public String getValue() {
         return value;
      }
   }

   /**
    * Other data of the test that should be reported but don't contain Operation execution times.
    */
   public static class TestResult implements Serializable {
      public final String name;
      public final Map<Integer, WorkerResult> workerResults;
      public final String aggregatedValue;
      public final boolean suspicious;
      private TestIteration iteration;

      /**
       * @param name Name of the result must be unique.
       * @param workerResults Results from each worker.
       * @param aggregatedValue Results from workers 'merged' together.
       * @param suspicious Flag that the result is unexpected and should be highlighted in the report.
       */
      public TestResult(String name, Map<Integer, WorkerResult> workerResults, String aggregatedValue, boolean suspicious) {
         this.name = name;
         this.workerResults = Collections.unmodifiableMap(workerResults);
         this.aggregatedValue = aggregatedValue;
         this.suspicious = suspicious;
      }

      private void setIteration(TestIteration iteration) {
         this.iteration = iteration;
      }

      public TestIteration getIteration() {
         return iteration;
      }
   }

   /**
    * Result data from single worker.
    */
   public static class WorkerResult implements Serializable {
      public final String value;
      public final boolean suspicious;

      public WorkerResult(String value, boolean suspicious) {
         this.value = value;
         this.suspicious = suspicious;
      }
   }

   public static class Stage implements Serializable {
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

   public static final class Property implements Serializable {
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

      private Object writeReplace() {
         return new PropertyProxy(name, definition, value);
      }
   }

   private static final class PropertyProxy implements Serializable {
      private String name;
      private Definition definition;
      private Object value;

      private PropertyProxy(String name, Definition definition, Object value) {
         this.name = name;
         this.definition = definition;
         this.value = value;
      }

      private void writeObject(ObjectOutputStream o) throws IOException {
         o.writeObject(name);
         o.writeObject(definition);
         if (value == null) {
            o.writeObject(null);
         } else if (value instanceof Serializable) {
            o.writeObject(value);
         } else {
            o.writeObject(String.valueOf(value));
         }
      }

      private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
         name = (String) in.readObject();
         definition = (Definition) in.readObject();
         value = in.readObject();
      }

      private Object readResolve() {
         return new Property(name, definition, value);
      }
   }
}
