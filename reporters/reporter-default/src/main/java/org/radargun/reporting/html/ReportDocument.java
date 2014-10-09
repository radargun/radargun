package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.Throughput;
import org.radargun.utils.Projections;

/**
 * Shows results of the tests executed in the benchmark. Also creates the image files displayed in this HTML document.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 2.0
 */
// TODO: reduce max report size in order to not overload browser with huge tables
public abstract class ReportDocument extends HtmlDocument {
   protected final Log log = LogFactory.getLog(getClass());

   // TODO: make us directly configurable through property delegate object
   private int elementCounter = 0;
   private int histogramBuckets = 40;
   private double histogramPercentile = 99d;
   private int histogramWidth = 800, histogramHeight = 600;

   protected final int maxConfigurations;
   protected final int maxIterations;
   protected final int maxClusters;

   protected boolean separateClusterCharts;
   protected final String testName;

   public ReportDocument(String targetDir, String testName, int maxConfigurations, int maxClusters, int maxIterations, boolean separateClusterCharts) {
      super(targetDir, String.format("test_%s.html", testName), "Test " + testName);

      this.testName = testName;
      this.maxConfigurations = maxConfigurations;
      this.maxClusters = maxClusters;
      this.maxIterations = maxIterations;
      this.separateClusterCharts = separateClusterCharts;
   }

   @Override
   protected void writeStyle() {
      write("TABLE { border-spacing: 0; border-collapse: collapse; }\n");
      write("TD { border: 1px solid gray; padding: 2px; }\n");
      write("TH { border: 1px solid gray; padding: 2px; }\n");
   }

   protected void createAndWriteCharts(String operation, String clusterSize) throws IOException {
      createChart(String.format("%s%s%s_%s%s_mean_dev.png", directory, File.separator, testName, operation, clusterSize),
                  operation, "Response time (ms)", StatisticType.MEAN_AND_DEV);
      createChart(String.format("%s%s%s_%s%s_throughput.png", directory, File.separator, testName, operation, clusterSize),
                  operation, "Operations/sec", StatisticType.ACTUAL_THROUGHPUT);
      write("<table><th style=\"text-align: center;border: 0px;\">Response time mean</th><th style=\"text-align: center;border: 0px;\">Actual throughput</th><tr>");
      write(String.format("<td style=\"border: 0px;\"><img src=\"%s_%s%s_mean_dev.png\" alt=\"%s\"/></div></div></td>\n", testName, operation, clusterSize, operation));
      write(String.format("<td style=\"border: 0px;\"><img src=\"%s_%s%s_throughput.png\" alt=\"%s\"/></div></div>\n", testName, operation, clusterSize, operation));
      write("</tr></table>");
   }

   protected void writeResult(Map<Report, List<Report.TestResult>> results) {
      write("<table>\n");
      if (maxIterations > 1) {
         write("<tr><th colspan=\"2\">&nbsp;</th>");
         Map.Entry<Report, List<Report.TestResult>> entry = results.entrySet().iterator().next();
         for (int iteration = 0; iteration < maxIterations; ++iteration) {
            String iterationValue;
            if (entry != null) {
               Report.TestResult testResult = entry.getValue().get(iteration);
               iterationValue = testResult != null && testResult.iterationName != null
                     ? testResult.iterationName + "=" + testResult.iterationValue : "iteration " + String.valueOf(iteration);
            } else {
               iterationValue = "iteration " + String.valueOf(iteration);
            }
            write(String.format("<th style=\"border-left-color: black; border-left-width: 2px;\">%s</th>", iterationValue));
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
      String rowStyle = suspect ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; " : "");
      rowStyle += "text-align: right; ";
      writeTD(value, rowStyle + "border-left-color: black; border-left-width: 2px;");
   }

   protected void createChart(String filename, String operation, String rangeAxisLabel, StatisticType statisticType) throws IOException {
      ComparisonChart chart = null;
      boolean xLabelClusterSize = !separateClusterCharts && (maxClusters > 1 || maxIterations <= 1);

      chart = generateChart(chart, operation, rangeAxisLabel, statisticType, xLabelClusterSize);

      chart.setWidth(Math.min(Math.max(maxConfigurations, maxIterations) * 100 + 200, 1800));
      chart.setHeight(Math.min(maxConfigurations * 100 + 200, 800));
      chart.save(filename);
   }

   protected ComparisonChart createComparisonChart(ComparisonChart chart, String testName, String operation, String rangeAxisLabel, StatisticType statisticType, Map<Report, List<Aggregation>> reportAggregationMap, boolean xLabelClusterSize) {
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         int iterationIndex = 0;
         for (Aggregation aggregation : entry.getValue()) {
            if (chart == null) {
               if ((maxClusters > 1 && !separateClusterCharts) || maxIterations> 1) {
                  chart = new LineChart(xLabelClusterSize ? "Cluster size" : (aggregation.iterationValue() != null ? aggregation.iterationName() : "Iteration"), rangeAxisLabel);
               } else {
                  chart = new BarChart("Cluster size", rangeAxisLabel);
               }
            }
            String columnKey = aggregation.iterationName() == null ? String.valueOf(iterationIndex) : aggregation.iterationValue();
            try {
               String categoryName = entry.getKey().getConfiguration().name;
               if (!separateClusterCharts && maxClusters > 1)
                  categoryName = String.format("%s_%s", categoryName, columnKey);
               if (!testName.isEmpty())
                  categoryName = String.format("%s_%s", categoryName, testName);
               OperationStats operationStats = aggregation.totalStats().getOperationsStats().get(operation);
               if (operationStats == null) continue;
               switch (statisticType) {
                  case MEAN_AND_DEV: {
                     MeanAndDev meanAndDev = operationStats.getRepresentation(MeanAndDev.class);
                     if (meanAndDev == null) continue;
                     chart.addValue(meanAndDev.mean / 1000000, meanAndDev.dev / 1000000, categoryName, xLabelClusterSize ? aggregation.nodeStats().size() : columnKey);
                     break;
                  }
                  case ACTUAL_THROUGHPUT: {
                     DefaultOutcome defaultOutcome = operationStats.getRepresentation(DefaultOutcome.class);
                     if (defaultOutcome == null) continue;
                     Throughput throughput = defaultOutcome.toThroughput(aggregation.totalThreads(), aggregation.totalStats().getEnd() - aggregation.totalStats().getBegin());
                     chart.addValue(throughput.actual / 1000000, 0, categoryName, xLabelClusterSize ? aggregation.nodeStats().size() : columnKey);
                  }
               }
            } finally {
               ++iterationIndex;
            }
         }
      }
      return chart;
   }

   protected void writeOperation(final String operation, Map<Report, List<Aggregation>> reportAggregationMap) {
      write("<table>\n");
      boolean hasHistograms = Projections.any(Projections.notNull(reportAggregationMap.values()), new Projections.Condition<List<Aggregation>>() {
         @Override
         public boolean accept(List<Aggregation> aggregations) {
            return Projections.any(Projections.notNull(aggregations), new Projections.Condition<Aggregation>() {
               @Override
               public boolean accept(Aggregation aggregation) {
                  OperationStats operationStats = aggregation.totalStats().getOperationsStats().get(operation);
                  return operationStats != null && operationStats.getRepresentation(Histogram.class, histogramBuckets, histogramPercentile) != null;
               }
            });
         }
      });
      int columns = hasHistograms ? 6 : 5;
      if (maxIterations > 1) {
         write("<tr><th colspan=\"2\">&nbsp;</th>");
         Map.Entry<Report, List<Aggregation>> entry = reportAggregationMap.entrySet().iterator().next();
         for (int iteration = 0; iteration < maxIterations; ++iteration) {
            String iterationValue;
            if (entry != null) {
               Aggregation aggregation = entry.getValue().get(iteration);
               iterationValue = aggregation != null && aggregation.iterationName() != null
                     ? aggregation.iterationName() + "=" + aggregation.iterationValue() : "iteration " + String.valueOf(iteration);
            } else {
               iterationValue = "iteration " + String.valueOf(iteration);
            }
            write(String.format("<th colspan=\"%d\" style=\"border-left-color: black; border-left-width: 2px;\">%s</th>", columns, iterationValue));
         }
         write("</tr>\n");
      }
      write("<tr><th colspan=\"2\">Configuration</th>\n");
      for (int i = 0; i < maxIterations; ++i) {
         write("<th style=\"text-align: center; border-left-color: black; border-left-width: 2px;\">requests</th>\n");
         write("<th style=\"text-align: center\">errors</th>\n");
         write("<th>mean</td><th>std.dev</th><th>throughput</th>\n");
         if (hasHistograms) {
            write("<th>histogram</th>\n");
         }
      }
      write("</tr>\n");
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         Report report = entry.getKey();

         int nodeCount = entry.getValue().isEmpty() ? 0 : entry.getValue().get(0).nodeStats().size();

         write("<tr><th style=\"text-align: left; cursor: pointer\" onClick=\"");
         for (int i = 0; i < nodeCount; ++i) {
            write("switch_visibility('e" + (elementCounter + i) + "'); ");
         }
         write(String.format("\">%s</th><th>%s</th>", report.getConfiguration().name, report.getCluster()));

         int iteration = 0;
         for (Aggregation aggregation : entry.getValue()) {
            Statistics statistics = aggregation.totalStats();
            OperationStats operationStats = statistics == null ? null : statistics.getOperationsStats().get(operation);
            long period = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
            writeRepresentations(operationStats, operation, entry.getKey().getCluster().getClusterIndex(), iteration, "total",
                                 aggregation.totalThreads(), period, hasHistograms, false, aggregation.anySuspect(operation));
            ++iteration;
         }

         write("</tr>\n");
         for (int node = 0; node < nodeCount; ++node) {
            write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th colspan=\"2\" style=\"text-align: right\">node%d</th>", elementCounter++, node));
            for (Aggregation aggregation : entry.getValue()) {
               Statistics statistics = node >= aggregation.nodeStats().size() ? null : aggregation.nodeStats().get(node);

               OperationStats operationStats = null;
               long period = 0;
               if (statistics != null) {
                  operationStats = statistics.getOperationsStats().get(operation);
                  period = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
               }
               int threads = node >= aggregation.nodeThreads().size() ? 0 : aggregation.nodeThreads().get(node);
               writeRepresentations(operationStats, operation, entry.getKey().getCluster().getClusterIndex(), iteration, "node" + node,
                                    threads, period, hasHistograms, false, aggregation.anySuspect(operation));
            }
            write("</tr>\n");
         }
      }
      write("</table><br>\n");
   }

   private void writeRepresentations(OperationStats operationStats, String operation, int cluster, int iteration, String node,
                                     int threads, long period, boolean hasHistograms, boolean gray, boolean suspect) {
      DefaultOutcome defaultOutcome = operationStats == null ? null : operationStats.getRepresentation(DefaultOutcome.class);
      Throughput throughput = defaultOutcome == null ? null : defaultOutcome.toThroughput(threads, period);
      MeanAndDev meanAndDev = operationStats == null ? null : operationStats.getRepresentation(MeanAndDev.class);
      Histogram histogram = operationStats == null ? null : operationStats.getRepresentation(Histogram.class, histogramBuckets, histogramPercentile);

      String rowStyle = suspect ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; " : "");
      rowStyle += "text-align: right; ";

      writeTD(defaultOutcome == null ? "&nbsp;" : String.valueOf(defaultOutcome.requests),
              rowStyle + "border-left-color: black; border-left-width: 2px;");
      writeTD(defaultOutcome == null ? "&nbsp;" : String.valueOf(defaultOutcome.errors), rowStyle);
      writeTD(meanAndDev == null ? "&nbsp;" : String.format("%.2f ms", toMillis(meanAndDev.mean)), rowStyle);
      writeTD(meanAndDev == null ? "&nbsp;" : String.format("%.2f ms", toMillis(meanAndDev.dev)), rowStyle);
      writeTD(throughput == null ? "&nbsp;" : String.format("%.0f reqs/s", throughput.actual), rowStyle);
      if (hasHistograms) {
         if (histogram == null) {
            writeTD("none", rowStyle);
         } else {
            String filename = String.format("histogram_%s_%s_%d_%d_%s.png", testName, operation, cluster, iteration, node);
            try {
               HistogramChart chart = new HistogramChart().setData(operation, histogram);
               chart.setWidth(histogramWidth).setHeight(histogramHeight);
               chart.save(directory + File.separator + filename);
               writeTD(String.format("<a href=\"%s\">show</a>", filename), rowStyle);
            } catch (IOException e) {
               log.error("Failed to generate chart " + filename, e);
               writeTD("error", rowStyle);
            }
         }
      }
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

   protected static enum StatisticType {
      MEAN_AND_DEV, ACTUAL_THROUGHPUT
   }

   protected abstract ComparisonChart generateChart(ComparisonChart chart, String operation, String rangeAxisLabel, StatisticType statisticType, boolean xLabelClusterSize);
}
