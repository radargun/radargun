package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Cluster;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.Throughput;

/**
 * Shows results of the tests executed in the benchmark.
 * Also creates the image files displayed in this HTML document.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
// TODO: reduce max report size in order to not overload browser with huge tables
public class TestReportDocument extends HtmlDocument {
   private final static Log log = LogFactory.getLog(TestReportDocument.class);

   private int maxIterations = 0;
   private int elementCounter = 0;
   private boolean separateClusterCharts;

   private final List<Report.Test> tests;
   private final String testName;
   private Map<Report, List<Aggregation>> aggregated = new TreeMap<Report, List<Aggregation>>();
   private Map<Integer, Map<Report, List<Aggregation>>> aggregatedByConfigs = new TreeMap<>();
   private Map<String, Map<Report, List<Report.TestResult>>> results = new TreeMap<String, Map<Report, List<Report.TestResult>>>();
   private Set<String> operations = new TreeSet<String>();
   private Set<Cluster> clusters = new TreeSet<Cluster>();

   public TestReportDocument(String targetDir, String testName, List<Report.Test> tests, boolean separateClusterCharts) {
      super(targetDir, String.format("test_%s.html", testName), "Test " + testName);
      this.tests = tests;
      this.testName = testName;
      this.separateClusterCharts = separateClusterCharts;
      initAggregations();
   }

   private void initAggregations() {
      for (Report.Test test : tests) {
         List<Aggregation> iterations = new ArrayList<Aggregation>();
         for (Report.TestIteration it : test.getIterations()) {
            Statistics totalStats = null;
            int totalThreads = 0;
            List<Statistics> nodeStats = new ArrayList<Statistics>();
            List<Integer> nodeThreads = new ArrayList<Integer>();
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
               iterations.add(new Aggregation(nodeStats, nodeThreads, totalStats, totalThreads));
               for (Map.Entry<String, OperationStats> op : totalStats.getOperationsStats().entrySet()) {
                  if (!op.getValue().isEmpty()) operations.add(op.getKey());
               }
            }

            if (it != null && it.getResults() != null) {
               for (Map.Entry<String, Report.TestResult> entry : it.getResults().entrySet()) {
                  Map<Report, List<Report.TestResult>> resultsByType = results.get(entry.getKey());
                  if (resultsByType == null) {
                     resultsByType = new TreeMap<Report, List<Report.TestResult>>();
                     results.put(entry.getKey(), resultsByType);
                  }
                  List<Report.TestResult> resultsList = resultsByType.get(test.getReport());
                  if (resultsList == null) {
                     resultsList = new ArrayList<Report.TestResult>();
                     resultsByType.put(test.getReport(), resultsList);
                  }
                  resultsList.add(entry.getValue());
               }
            }
         }
         aggregated.put(test.getReport(), iterations);
         int clusterSize = test.getReport().getCluster().getSize();
         Map<Report, List<Aggregation>> reportAggregationMap = aggregatedByConfigs.get(clusterSize);
         if (reportAggregationMap == null) {
            reportAggregationMap = new HashMap<>();
         }
         reportAggregationMap.put(test.getReport(), iterations);
         aggregatedByConfigs.put(clusterSize, reportAggregationMap);
         maxIterations = Math.max(maxIterations, iterations.size());
         clusters.add(test.getReport().getCluster());
      }
   }

   @Override
   protected void writeStyle() {
      write("TABLE { border-spacing: 0; border-collapse: collapse; }\n");
      write("TD { border: 1px solid gray; padding: 2px; }\n");
      write("TH { border: 1px solid gray; padding: 2px; }\n");
   }

   public void writeTest() throws IOException {
      writeTag("h1", "Test " + testName);
      for (Map.Entry<String, Map<Report, List<Report.TestResult>>> result : results.entrySet()) {
         writeTag("h2", result.getKey());
         writeResult(result.getValue());
      }
      for (String operation : operations) {
         writeTag("h2", operation);
         if (separateClusterCharts) {
            for (Map.Entry<Integer, Map<Report, List<Aggregation>>> entry : aggregatedByConfigs.entrySet()) {
               createAndWriteCharts(operation, entry.getValue(), "_" + entry.getKey());
            }
         } else {
            createAndWriteCharts(operation, aggregated, "");
         }
      }
   }

   private void createAndWriteCharts(String operation, Map<Report, List<Aggregation>> reportAggregationMap, String clusterSize) throws IOException {
      createChart(String.format("%s%s%s_%s%s_mean_dev.png", directory, File.separator, testName, operation, clusterSize),
            operation, "Response time (ms)", StatisticType.MEAN_AND_DEV, reportAggregationMap);
      createChart(String.format("%s%s%s_%s%s_throughput.png", directory, File.separator, testName, operation, clusterSize),
            operation,  "Operations/sec", StatisticType.ACTUAL_THROUGHPUT, reportAggregationMap);
      write("<table><tr>");
      write(String.format("<td style=\"border: 0px;\"><img src=\"%s_%s%s_mean_dev.png\" alt=\"%s\"/></div></div></td>\n", testName, operation, clusterSize, operation));
      write(String.format("<td style=\"border: 0px;\"><img src=\"%s_%s%s_throughput.png\" alt=\"%s\"/></div></div>\n", testName, operation, clusterSize, operation));
      write("</tr></table>");
      writeOperation(operation, reportAggregationMap);
   }

   private void writeResult(Map<Report, List<Report.TestResult>> results) {
      write("<table>\n");
      if (maxIterations > 1) {
         write("<tr><th colspan=\"2\">&nbsp;</th>");
         for (int iteration = 0; iteration < maxIterations; ++iteration) {
            write(String.format("<th style=\"border-left-color: black; border-left-width: 2px;\">iteration %d</th>", iteration));
         }
         write("</tr>\n");
      }
      write("<tr><th colspan=\"2\">Configuration</th>\n");
      for (int i = 0; i < maxIterations; ++i) {
         write("<th style=\"text-align: center; border-left-color: black; border-left-width: 2px;\">Value</th>\n");
      }
      write("</tr>\n");
      for (Map.Entry<Report, List<Report.TestResult>> entry : results.entrySet()) {
         Report report = entry.getKey();

         int nodeCount = entry.getValue().isEmpty() ? 0 : entry.getValue().get(0).slaveResults.size();

         write("<tr><th style=\"text-align: left; cursor: pointer\" onClick=\"");
         for (int i = 0; i < nodeCount; ++i) {
            write("switch_visibility('e" + (elementCounter + i) + "'); ");
         }
         write(String.format("\">%s</th><th>%s</th>", report.getConfiguration().name, report.getCluster()));
         for (Report.TestResult result : entry.getValue()) {
            writeResult(result.aggregatedValue, false, result.suspicious);
         }
         write("</tr>\n");
         for (int node = 0; node < nodeCount; ++node) {
            write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th colspan=\"2\" style=\"text-align: right\">node%d</th>", elementCounter++, node));
            for (Report.TestResult result : entry.getValue()) {
               Report.SlaveResult sr = result.slaveResults.get(node);
               writeResult(sr.value, false, sr.suspicious);
            }
            write("</tr>\n");
         }
      }
      write("</table><br>\n");
   }

   private void writeResult(String value, boolean gray, boolean suspect) {
      String rowStyle = suspect ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; ": "");
      rowStyle += "text-align: right; ";
      writeTD(value, rowStyle + "border-left-color: black; border-left-width: 2px;");
   }

   private void createChart(String filename, String operation, String rangeAxisLabel, StatisticType statisticType, Map<Report, List<Aggregation>> reportAggregationMap) throws IOException {
      ComparisonChart chart;
      boolean xLabelClusterSize = !separateClusterCharts && (clusters.size() > 1 || maxIterations <= 1);
      if ((clusters.size() > 1 && !separateClusterCharts) || maxIterations > 1) {
         chart = new LineChart(xLabelClusterSize ? "Cluster size" : "Iteration", rangeAxisLabel);
      } else {
         chart = new BarChart("Cluster size", rangeAxisLabel);
      }
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         int iteration = 0;
         for (Aggregation aggregation : entry.getValue()) {
            try {
               String categoryName = entry.getKey().getConfiguration().name;
               if (!separateClusterCharts && clusters.size() > 1) categoryName = String.format("%s_%s", categoryName, iteration);
               OperationStats operationStats = aggregation.totalStats.getOperationsStats().get(operation);
               if (operationStats == null) continue;
               switch (statisticType) {
                  case MEAN_AND_DEV: {
                     MeanAndDev meanAndDev = operationStats.getRepresentation(MeanAndDev.class);
                     if (meanAndDev == null) continue;
                     chart.addValue(meanAndDev.mean / 1000000, meanAndDev.dev / 1000000, categoryName, xLabelClusterSize ? aggregation.nodeStats.size() : iteration);
                     break;
                  }
                  case ACTUAL_THROUGHPUT: {
                     DefaultOutcome defaultOutcome = operationStats.getRepresentation(DefaultOutcome.class);
                     if (defaultOutcome == null) continue;
                     Throughput throughput = defaultOutcome.toThroughput(aggregation.totalThreads, aggregation.totalStats.getEnd() - aggregation.totalStats.getBegin());
                     chart.addValue(throughput.actual / 1000000, 0, categoryName, xLabelClusterSize ? aggregation.nodeStats.size() : iteration);
                  }
               }
            } finally {
               ++iteration;
            }
         }
      }
      chart.setWidth(Math.min(Math.max(tests.size(), maxIterations) * 100 + 200, 1800));
      chart.setHeight(Math.min(tests.size() * 100 + 200, 800));
      chart.save(filename);
   }

   private void writeOperation(String operation, Map<Report, List<Aggregation>> reportAggregationMap) {
      write("<table>\n");
      if (maxIterations > 1) {
         write("<tr><th colspan=\"2\">&nbsp;</th>");
         for (int iteration = 0; iteration < maxIterations; ++iteration) {
            write(String.format("<th colspan=\"6\" style=\"border-left-color: black; border-left-width: 2px;\">iteration %d</th>", iteration));
         }
         write("</tr>\n");
      }
      write("<tr><th colspan=\"2\">Configuration</th>\n");
      for (int i = 0; i < maxIterations; ++i) {
         write("<th style=\"text-align: center; border-left-color: black; border-left-width: 2px;\">requests</th>\n");
         write("<th style=\"text-align: center\">errors</th>\n");
         write("<th>mean</td><th>std.dev</th><th>theoretical&nbsp;TP</th><th>actual&nbsp;TP</th>\n");
      }
      write("</tr>\n");
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         Report report = entry.getKey();

         int nodeCount = entry.getValue().isEmpty() ? 0 : entry.getValue().get(0).nodeStats.size();

         write("<tr><th style=\"text-align: left; cursor: pointer\" onClick=\"");
         for (int i = 0; i < nodeCount; ++i) {
            write("switch_visibility('e" + (elementCounter + i) + "'); ");
         }
         write(String.format("\">%s</th><th>%s</th>", report.getConfiguration().name, report.getCluster()));

         for (Aggregation aggregation : entry.getValue()) {
            Statistics statistics = aggregation.totalStats;
            OperationStats operationStats = statistics == null ? null : statistics.getOperationsStats().get(operation);
            DefaultOutcome defaultOutcome = operationStats == null ? null : operationStats.getRepresentation(DefaultOutcome.class);
            Throughput throughput = defaultOutcome == null ? null : defaultOutcome.toThroughput(aggregation.totalThreads,
                  TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin()));
            MeanAndDev meanAndDev = operationStats == null ? null : operationStats.getRepresentation(MeanAndDev.class);

            writeRepresentations(defaultOutcome, meanAndDev, throughput, false, aggregation.anySuspect(operation));
         }

         write("</tr>\n");
         for (int node = 0; node < nodeCount; ++node) {
            write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th colspan=\"2\" style=\"text-align: right\">node%d</th>", elementCounter++, node));
            for (Aggregation aggregation : entry.getValue()) {
               Statistics statistics = node >= aggregation.nodeStats.size() ? null : aggregation.nodeStats.get(node);
               Integer threads = node >= aggregation.nodeThreads.size() ? null : aggregation.nodeThreads.get(node);

               OperationStats operationStats = statistics == null ? null : statistics.getOperationsStats().get(operation);
               DefaultOutcome defaultOutcome = operationStats == null ? null : operationStats.getRepresentation(DefaultOutcome.class);
               Throughput throughput = defaultOutcome == null ? null : defaultOutcome.toThroughput(threads == null ? 0 : threads,
                     TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin()));
               MeanAndDev meanAndDev = operationStats == null ? null : operationStats.getRepresentation(MeanAndDev.class);

               writeRepresentations(defaultOutcome, meanAndDev, throughput, true, aggregation.isSuspect(node, operation));
            }
            write("</tr>\n");
         }
      }
      write("</table><br>\n");
   }

   private void writeRepresentations(DefaultOutcome defaultOutcome, MeanAndDev meanAndDev, Throughput throughput,
                                     boolean gray, boolean suspect) {
      String rowStyle = suspect ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; ": "");
      rowStyle += "text-align: right; ";

      writeTD(defaultOutcome == null ? "&nbsp;" : String.valueOf(defaultOutcome.requests),
            rowStyle + "border-left-color: black; border-left-width: 2px;");
      writeTD(defaultOutcome == null ? "&nbsp;" : String.valueOf(defaultOutcome.errors), rowStyle);
      writeTD(meanAndDev == null ? "&nbsp;" : String.format("%.2f ms", toMillis(meanAndDev.mean)), rowStyle);
      writeTD(meanAndDev == null ? "&nbsp;" : String.format("%.2f ms", toMillis(meanAndDev.dev)), rowStyle);
      writeTD(throughput == null ? "&nbsp;" : String.format("%.0f reqs/s", throughput.theoretical), rowStyle);
      writeTD(throughput == null ? "&nbsp;" : String.format("%.0f reqs/s", throughput.actual), rowStyle);
   }

   private double toMillis(double nanos) {
      return nanos / 1000000;
   }

   private void writeTD(String content, String style) {
      write("<td style=\"" + style + "\">");
      write(content);
      write("</td>\n");
   }

   @Override
   protected void writeScripts() {
      write("function switch_visibility(id) {\n");
      write("    var element = document.getElementById(id);\n");
      write("    if (element == null) return;\n");
      write("    if (element.style.visibility == 'collapse') {\n");
      write("        element.style.visibility = 'visible';\n");
      write("    } else {\n");
      write("        element.style.visibility = 'collapse';\n");
      write("    }\n}\n");
   }

   private static class Aggregation {
      final List<Statistics> nodeStats;
      final Statistics totalStats;
      final List<Integer> nodeThreads;
      final int totalThreads;

      public Aggregation(List<Statistics> nodeStats, List<Integer> nodeThreads, Statistics totalStats, int totalThreads) {
         this.nodeStats = nodeStats;
         this.nodeThreads = nodeThreads;
         this.totalStats = totalStats;
         this.totalThreads = totalThreads;
      }

      public boolean anySuspect(String operation) {
         for (int i = 0; i < nodeStats.size(); ++i) {
            if (isSuspect(i, operation)) {
               return true;
            }
         }
         return false;
      }

      public boolean isSuspect(int node, String operation) {
         Statistics ns;
         if (node >= nodeStats.size() || (ns = nodeStats.get(node)) == null) {
            return false;
         }
         OperationStats nos = ns.getOperationsStats().get(operation);
         OperationStats tos = totalStats.getOperationsStats().get(operation);
         if (nos == null) {
            return tos != null;
         }
         DefaultOutcome ndo = nos.getRepresentation(DefaultOutcome.class);
         DefaultOutcome tdo = nos.getRepresentation(DefaultOutcome.class);
         if (ndo == null ) {
            return tdo != null;
         }
         return ndo.requests < tdo.requests * 4 / 5 || ndo.requests > tdo.requests * 5 / 4;
      }
   }

   private static enum StatisticType {
      MEAN_AND_DEV, ACTUAL_THROUGHPUT
   }
}
