package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Cluster;
import org.radargun.reporting.Report;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.Throughput;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
// TODO: reduce max report size in order to not overload browser with huge tables
public class TestReportDocument extends HtmlDocument {
   private final List<Report.Test> tests;
   private final String testName;
   private Map<Report, List<Aggregation>> aggregated = new TreeMap<Report, List<Aggregation>>();
   private int maxIterations = 0;
   private Set<String> operations = new TreeSet<String>();
   private Set<Cluster> clusters = new TreeSet<Cluster>();

   private int elementCounter = 0;

   public TestReportDocument(String targetDir, String testName, List<Report.Test> tests) {
      super(targetDir, String.format("test_%s.html", testName), "Test " + testName);
      this.tests = tests;
      this.testName = testName;
      for (Report.Test test : tests) {
         List<Aggregation> iterations = new ArrayList<Aggregation>();
         for (Report.TestIteration it : test.getIterations()) {
            Statistics totalStats = null;
            int totalThreads = 0;
            List<Statistics> nodeStats = new ArrayList<Statistics>();
            List<Integer> nodeThreads = new ArrayList<Integer>();
            for (Map.Entry<Integer, List<Statistics>> entry : it.statistics.entrySet()) {
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
            iterations.add(new Aggregation(nodeStats, nodeThreads, totalStats, totalThreads));
            for (Map.Entry<String, OperationStats> op : totalStats.getOperationsStats().entrySet()) {
               if (!op.getValue().isEmpty()) operations.add(op.getKey());
            }
         }
         aggregated.put(test.getReport(), iterations);
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
      for (String operation : operations) {
         writeTag("h2", operation);
         createChart(String.format("%s%s%s_%s.png", directory, File.separator, testName, operation), operation);
         write(String.format("<div style=\"text-align: left; display: inline-block;\"><div style=\"display: table; text-align: center\">" +
               "<img src=\"%s_%s.png\" alt=\"%s\"/></div></div>\n", testName, operation, operation));
         writeOperation(operation);
      }
   }

   private void createChart(String filename, String operation) throws IOException {
      ComparisonChart chart;
      boolean xLabelClusterSize = clusters.size() > 1 || maxIterations <= 1;
      if (clusters.size() > 1 || maxIterations > 1) {
         chart = new LineChart(xLabelClusterSize ? "Cluster size" : "Iteration");
      } else {
         chart = new BarChart();
      }
      for (Map.Entry<Report, List<Aggregation>> entry : aggregated.entrySet()) {
         int iteration = 0;
         for (Aggregation aggregation : entry.getValue()) {
            try {
               String categoryName = entry.getKey().getConfiguration().name;
               if (clusters.size() > 1) categoryName = String.format("%s_%s", categoryName, iteration);

               OperationStats operationStats = aggregation.totalStats.getOperationsStats().get(operation);
               if (operationStats == null) continue;
               MeanAndDev meanAndDev = operationStats.getRepresentation(MeanAndDev.class);
               if (meanAndDev == null) continue;

               chart.add(categoryName, xLabelClusterSize ? aggregation.nodeStats.size() : iteration, meanAndDev);
            } finally {
               ++iteration;
            }
         }
      }
      chart.width = Math.min(Math.max(tests.size(), maxIterations) * 100 + 200, 1800);
      chart.height = Math.min(tests.size() * 100 + 200, 800);
      chart.save(filename);
   }

   private void writeOperation(String operation) {
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
      for (Map.Entry<Report, List<Aggregation>> entry : aggregated.entrySet()) {
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

}
