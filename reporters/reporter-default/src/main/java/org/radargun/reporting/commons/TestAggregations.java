package org.radargun.reporting.commons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.radargun.Operation;
import org.radargun.config.Cluster;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;

/**
 * Perform aggregations and holds them
 *
 * @author Vitalii Chepeliuk &lt;vchepeli@redhat.com&gt;
 * @since 2.0
 */
public class TestAggregations {
   private Log log = LogFactory.getLog(getClass());

   public final String testName;
   public final String iterationsName;

   private Map<Report, List<Aggregation>> byReports = new TreeMap<>();
   private Map<Integer, Map<Report, List<Aggregation>>> byClusterSize = new TreeMap<>();
   private Map<String, Map<Report, List<Report.TestResult>>> results = new TreeMap<>();

   private int maxIterations = 0;
   private Set<String> operations = new TreeSet<>();
   private Set<String> operationGroups = new TreeSet<>();
   private Set<Cluster> clusters = new TreeSet<>();

   public TestAggregations(String testName, List<Report.Test> tests){
      this.testName = testName;
      Set<String> iterationsNames = new TreeSet<>();

      for (Report.Test test : tests) {
         if (test.iterationsName != null) {
            iterationsNames.add(test.iterationsName);
         }
         List<Aggregation> iterations = new ArrayList<Aggregation>();
         for (Report.TestIteration it : test.getIterations()) {
            addIteration(test, iterations, it);
         }
         byReports.put(test.getReport(), iterations);
         int clusterSize = test.getReport().getCluster().getSize();
         Map<Report, List<Aggregation>> reportAggregationMap = byClusterSize.get(clusterSize);
         if (reportAggregationMap == null) {
            reportAggregationMap = new TreeMap<>();
         }
         reportAggregationMap.put(test.getReport(), iterations);
         byClusterSize.put(clusterSize, reportAggregationMap);
         maxIterations = Math.max(maxIterations, iterations.size());
         clusters.add(test.getReport().getCluster());
      }

      if (iterationsNames.isEmpty()) {
         iterationsName = null;
      } else {
         StringBuilder sb = new StringBuilder();
         for (String name : iterationsNames) {
            if (sb.length() != 0) sb.append(", ");
            sb.append(name);
         }
         iterationsName = sb.toString();
      }
   }

   private void addIteration(Report.Test test, List<Aggregation> iterations, Report.TestIteration it) {
      Statistics totalStats = null;
      int totalThreads = 0;
      List<Statistics> nodeStats = new ArrayList<>();
      List<Integer> nodeThreads = new ArrayList<>();
      for (Map.Entry<Integer, List<Statistics>> entry : it.getStatistics()) {
         int slaveIndex = entry.getKey();
         List<Statistics> list = entry.getValue();

         Statistics ns = null;
         for (Statistics s : list) {
            if (ns == null) {
               ns = s.copy();
            } else {
               ns.merge(s);
            }
         }

         if (ns != null) {
            while (nodeStats.size() <= slaveIndex) {
               nodeStats.add(null);
               nodeThreads.add(0);
            }
            nodeStats.set(slaveIndex, ns);
            nodeThreads.set(slaveIndex, list.size());

            if (totalStats == null) {
               totalStats = ns.copy();
            } else {
               totalStats.merge(ns);
            }
         }
         totalThreads += list.size();
      }
      if (totalStats == null) {
         log.warn("There are no stats for this iteration");
      } else {
         if (test.getGroupOperationsMap() != null) {
            for (Map.Entry<String, Set<Operation>> op : test.getGroupOperationsMap().entrySet()) {
               totalStats.registerOperationsGroup(op.getKey(), op.getValue());
            }
         }
         iterations.add(new Aggregation(nodeStats, nodeThreads, totalStats, totalThreads, it));
         for (Map.Entry<String, OperationStats> op : totalStats.getOperationsStats().entrySet()) {
            if (!op.getValue().isEmpty()) operations.add(op.getKey());
         }
         operationGroups.addAll(totalStats.getOperationStatsForGroups().keySet());
      }

      if (it != null && it.getResults() != null) {
         for (Map.Entry<String, Report.TestResult> entry : it.getResults().entrySet()) {
            Map<Report, List<Report.TestResult>> resultsByType = results.get(entry.getKey());
            if (resultsByType == null) {
               resultsByType = new TreeMap<>();
               results.put(entry.getKey(), resultsByType);
            }
            List<Report.TestResult> resultsList = resultsByType.get(test.getReport());
            if (resultsList == null) {
               resultsList = new ArrayList<>();
               resultsByType.put(test.getReport(), resultsList);
            }
            resultsList.add(entry.getValue());
         }
      }
   }

   public Map<Report, List<Aggregation>> byReports() {
      return byReports;
   }

   public Map<Integer, Map<Report, List<Aggregation>>> byClusterSize() {
      return byClusterSize;
   }

   public Map<String, Map<Report, List<Report.TestResult>>> results() {
      return results;
   }

   public Set<String> getAllOperations() {
      return Collections.unmodifiableSet(operations);
   }

   public Set<String> getOperationGroups() {
      return Collections.unmodifiableSet(operationGroups);
   }

   public Set<Cluster> getAllClusters() {
      return Collections.unmodifiableSet(clusters);
   }

   public int getMaxIterations() {
      return maxIterations;
   }
}

