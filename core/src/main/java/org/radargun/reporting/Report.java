package org.radargun.reporting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.radargun.config.Cluster;
import org.radargun.config.Configuration;
import org.radargun.stats.Statistics;

/**
 * Report from single test, e.g. one stage, or homogenous operations during multiple stages
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Report implements Comparable<Report> {
   /* Configuration part */
   public Configuration configuration;
   public Cluster cluster;
   /* Slave configurations */
   public Map<Integer, Map<String, Properties>> normalizedSlaveConfigurations = new HashMap<Integer, Map<String, Properties>>();
   public Map<Integer, Map<String, byte[]>> originalSlaveConfigurations = new HashMap<Integer, Map<String, byte[]>>();
   /* Results part */
   public List<Timeline> timelines = new ArrayList<Timeline>();
   /* Test name - iterations */
   public Map<String, Test> tests = new LinkedHashMap<String, Test>();

   public Report(Configuration configuration, Cluster cluster) {
      this.configuration = configuration;
      this.cluster = cluster;
      this.timelines.add(new Timeline(-1));
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

      public void addIterations(int slaveIndex, List<List<Statistics>> results) {
         ensureIterations(results.size());
         for (int i = 0; i < results.size(); ++i) {
            iterations.get(i).statistics.put(slaveIndex, results.get(i));
            iterations.get(i).threadCount += results.get(i).size();
         }
      }

      private void ensureIterations(int size) {
         iterations.ensureCapacity(size);
         for (int i = iterations.size(); i < size; ++i) iterations.add(new TestIteration());
      }

      public List<TestIteration> getIterations() {
         return Collections.unmodifiableList(iterations);
      }

      public Report getReport() {
         return Report.this;
      }
   }

   public static class TestIteration {
      /* slave index - statistics from threads */
      public Map<Integer, List<Statistics>> statistics = new HashMap<Integer, List<Statistics>>();
      public int threadCount;
   }
}
