package org.radargun.reporting.commons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.Operation;
import org.radargun.config.Cluster;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;

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

   public TestAggregations(String testName, List<Report.Test> tests) {
      this.testName = testName;
      Set<String> iterationsNames = new TreeSet<>();

      for (Report.Test test : tests) {
         if (test.iterationsName != null) {
            iterationsNames.add(test.iterationsName);
         }
         List<Aggregation> iterations = new ArrayList<>();
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
      AtomicInteger totalThreads = new AtomicInteger();
      List<Statistics> nodeStats = new ArrayList<>();
      List<Integer> nodeThreads = new ArrayList<>();
      Optional<Statistics> totalStats = it.getStatistics().stream()
         .map(entry -> {
            int slaveIndex = entry.getKey();
            List<Statistics> list = entry.getValue();
            totalThreads.addAndGet(list.size());
            return list.stream().reduce(Statistics.MERGE).map(ns -> {
               while (nodeStats.size() <= slaveIndex) {
                  nodeStats.add(null);
                  nodeThreads.add(0);
               }
               nodeStats.set(slaveIndex, ns);
               nodeThreads.set(slaveIndex, list.size());
               return ns;
            });
         })
         .filter(Optional::isPresent).map(Optional::get).reduce(Statistics.MERGE);

      if (!totalStats.isPresent()) {
         log.warn("There are no stats for this iteration");
      } else {
         if (test.getGroupOperationsMap() != null && nodeStats != null && totalStats != null) {
            for (Map.Entry<String, Set<Operation>> op : test.getGroupOperationsMap().entrySet()) {
               //register groups for thread statistics
               totalStats.get().registerOperationsGroup(op.getKey(), op.getValue());
               //register groups for node statistics
               nodeStats.stream().filter(stats -> stats != null).forEach(ns -> ns.registerOperationsGroup(op.getKey(), op.getValue()));
               //register groups for thread statistics
               for (Map.Entry<Integer, List<Statistics>> mapEntry : it.getStatistics()) {
                  for (Statistics ts : mapEntry.getValue()) {
                     if (ts != null) {
                        ts.registerOperationsGroup(op.getKey(), op.getValue());
                     }
                  }
               }
            }
         }

         iterations.add(new Aggregation(nodeStats, nodeThreads, totalStats.get(), totalThreads.get(), test.getReport(), it));
         for (String operation : totalStats.get().getOperations()) {
            DefaultOutcome defaultOutcome = totalStats.get().getRepresentation(operation, DefaultOutcome.class);
            if (defaultOutcome == null || defaultOutcome.requests > 0) {
               operations.add(operation);
            }
         }

         operationGroups.addAll(totalStats.get().getOperationStatsForGroups().get(0).keySet());

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

   public Set<Cluster> getAllClusters() {
      return Collections.unmodifiableSet(clusters);
   }

   public int getMaxIterations() {
      return maxIterations;
   }

   public Set<String> getOperationGroups() {
      return Collections.unmodifiableSet(operationGroups);
   }
}
