package org.radargun.reporting;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.radargun.stressors.SimpleStatistics;
import org.radargun.utils.Table;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BenchmarkResult {
   Table<String, Integer, SortedMap<Integer, Iteration>> configResults = new Table<String, Integer, SortedMap<Integer, Iteration>>();

   public void addNodeStats(String productName, String configName, int clusterSize, int iteration, int node, SimpleStatistics nodeStats) {
      Iteration itResults = ensureIteration(productName, configName, clusterSize, iteration);
      itResults.addNodeStats(node, nodeStats);
   }

   private Iteration ensureIteration(String productName, String configName, int clusterSize, int iteration) {
      String name = getProductConfig(productName, configName);
      SortedMap<Integer, Iteration> iterations = configResults.get(name, clusterSize);
      if (iterations == null) {
         configResults.put(name, clusterSize, iterations = new TreeMap<Integer, Iteration>());
      }
      Iteration itResults = iterations.get(iteration);
      if (itResults == null) {
         iterations.put(iteration, itResults = new Iteration());
      }
      return itResults;
   }

   private String getProductConfig(String productName, String configName) {
      return String.format("%s (%s)", productName, configName);
   }

   public void addHistogramData(String productName, String configName, int clusterSize, int iteration, String requestType, boolean tx, String[] ranges, String[] counts) {
      Iteration itResults = ensureIteration(productName, configName, clusterSize, iteration);
      itResults.addHistogramData(getProductConfig(productName, configName), clusterSize, iteration, requestType, tx, ranges, counts);
   }

   public Set<Integer> getClusterSizes() {
      return configResults.columnKeys();
   }

   public SortedSet<Integer> getAllIterations() {
      SortedSet<Integer> iterations = new TreeSet<Integer>();
      for (String config : configResults.rowKeys()) {
         for (SortedMap<Integer, Iteration> its : configResults.getRow(config).values()) {
            iterations.addAll(its.keySet());
         }
      }
      return iterations;
   }

   public int getMaxIterations() {
      int maxIterations = 0;
      for (String config : configResults.rowKeys()) {
         for (SortedMap<Integer, Iteration> iterations : configResults.getRow(config).values()) {
            maxIterations = Math.max(maxIterations, iterations.size());
         }
      }
      return maxIterations;
   }

   public Map<String, HistogramData> getHistograms(String config, int clusterSize, int iteration) {
      return configResults.get(config, clusterSize).get(iteration).histograms;
   }

   public Set<String> getConfigs() {
      return configResults.rowKeys();
   }

   public Set<Integer> getIterations(String config, int clusterSize) {
      return configResults.get(config, clusterSize).keySet();
   }

   public SimpleStatistics getAggregatedStats(String config, int clusterSize, int iteration) {
      return configResults.get(config, clusterSize).get(iteration).aggregatedStats;
   }

   public int getThreadCount(String config, int clusterSize, int iteration) {
      return configResults.get(config, clusterSize).get(iteration).threads;
   }

   public Set<String> getRequestTypes() {
      Set<String> requestTypes = new HashSet<String>();
      for (String config : configResults.rowKeys()) {
         for (SortedMap<Integer, Iteration> iterations : configResults.getRow(config).values()) {
            for (Iteration i : iterations.values()) {
               requestTypes.addAll(i.aggregatedStats.getUsedOperations());
            }
         }
      }
      return requestTypes;
   }

   public void setThreads(String productName, String configName, int clusterSize, int iteration, int node, int threads) {
      Iteration itResult = ensureIteration(productName, configName, clusterSize, iteration);
      itResult.setThreads(node, threads);
   }

   public int getMaxClusterSize() {
      int maxSize = 0;
      for (int size : configResults.columnKeys()) {
         maxSize = Math.max(size, maxSize);
      }
      return maxSize;
   }

   public SimpleStatistics getNodeStats(String config, int clusterSize, int iteration, int node) {
      return configResults.get(config, clusterSize).get(iteration).nodeStats.get(node);
   }

   private static class Iteration {
      SortedMap<Integer, SimpleStatistics> nodeStats = new TreeMap<Integer, SimpleStatistics>();
      SimpleStatistics aggregatedStats = new SimpleStatistics();
      SortedMap<String, HistogramData> histograms = new TreeMap<String, HistogramData>();
      int threads = -1;

      public void addNodeStats(int node, SimpleStatistics stats) {
         if (nodeStats.put(node, stats) != null) {
            throw new IllegalStateException();
         }
         aggregatedStats.merge(stats);
      }

      public void addHistogramData(String config, int clusterSize, int iteration, String requestType, boolean tx, String[] ranges, String[] counts) {
         HistogramData hist = histograms.get(requestType);
         if (hist == null) {
            histograms.put(requestType, hist = new HistogramData(config, clusterSize, iteration, requestType, tx));
         }
         hist.add(ranges, counts);
      }

      public void setThreads(int node, int threads) {
         if (this.threads < 0) {
            this.threads = threads;
         } else if (threads != this.threads) {
            throw new IllegalArgumentException("Unexpected: other node had " + this.threads + ", this has " + threads);
         }
      }
   }
}
