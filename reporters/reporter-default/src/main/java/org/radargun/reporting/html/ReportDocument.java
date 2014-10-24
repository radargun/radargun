package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.Percentile;
import org.radargun.stats.representation.Throughput;
import org.radargun.utils.Projections;
import org.radargun.utils.Utils;

/**
 * Shows results of the tests executed in the benchmark. Also creates the image files displayed in this HTML document.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 2.0
 */
// TODO: reduce max report size in order to not overload browser with huge tables
public abstract class ReportDocument extends HtmlDocument {
   protected final Log log = LogFactory.getLog(getClass());

   private int elementCounter = 0;

   protected final int maxConfigurations;
   protected final int maxIterations;
   protected final int maxClusters;

   protected final Configuration configuration;
   protected final String testName;

   public ReportDocument(String targetDir, String testName, int maxConfigurations, int maxClusters, int maxIterations, Configuration configuration) {
      super(targetDir, String.format("test_%s.html", testName), "Test " + testName);

      this.testName = testName;
      this.maxConfigurations = maxConfigurations;
      this.maxClusters = maxClusters;
      this.maxIterations = maxIterations;
      this.configuration = configuration;
   }

   @Override
   protected void writeStyle() {
      write("TABLE { border-spacing: 0; border-collapse: collapse; }\n");
      write("TD { border: 1px solid gray; padding: 2px; }\n");
      write("TH { border: 1px solid gray; padding: 2px; }\n");
   }

   protected void createAndWriteCharts(String operation, int clusterSize) throws IOException {
      String suffix = clusterSize > 0 ? "_" + clusterSize : "";
      createChart(String.format("%s%s%s_%s%s_mean_dev.png", directory, File.separator, testName, operation, suffix),
            clusterSize, operation, "Response time (ms)", StatisticType.MEAN_AND_DEV);
      createChart(String.format("%s%s%s_%s%s_throughput.png", directory, File.separator, testName, operation, suffix),
            clusterSize, operation, "Operations/sec", StatisticType.ACTUAL_THROUGHPUT);
      write("<table><th style=\"text-align: center;border: 0px;\">Response time mean</th><th style=\"text-align: center;border: 0px;\">Actual throughput</th><tr>");
      write(String.format("<td style=\"border: 0px;\"><img src=\"%s_%s%s_mean_dev.png\" alt=\"%s\"/></div></div></td>\n", testName, operation, suffix, operation));
      write(String.format("<td style=\"border: 0px;\"><img src=\"%s_%s%s_throughput.png\" alt=\"%s\"/></div></div>\n", testName, operation, suffix, operation));
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
               String iterationsName = testResult.getIteration().test.iterationsName;
               iterationValue = testResult != null && iterationsName != null
                     ? iterationsName + "=" + testResult.getIteration().getValue() : "iteration " + String.valueOf(iteration);
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

   protected void createChart(String filename, int clusterSize, String operation, String rangeAxisLabel, StatisticType statisticType) throws IOException {
      ComparisonChart chart = generateChart(clusterSize, operation, rangeAxisLabel, statisticType);

      chart.setWidth(Math.min(Math.max(maxConfigurations, maxIterations) * 100 + 200, 1800));
      chart.setHeight(Math.min(maxConfigurations * 100 + 200, 800));
      chart.save(filename);
   }

   protected ComparisonChart createComparisonChart(String iterationsName, String rangeAxisLabel) {
      ComparisonChart chart;
      // We've simplified the rule: when we have more iterations, it's always line chart,
      // with tests/sizes included in the categoryName and iterations on domain axis.
      // When there's only one iteration, we put cluster sizes on domain axis but use bar chart.
      if (maxIterations > 1) {
         chart = new LineChart(iterationsName != null ? iterationsName : "Iteration", rangeAxisLabel);
      } else {
         chart = new BarChart("Cluster size", rangeAxisLabel);
      }
      return chart;
   }

   protected void addToChart(ComparisonChart chart, String subCategory, String operation, StatisticType statisticType, Map<Report, List<Aggregation>> reportAggregationMap) {
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         for (Aggregation aggregation : entry.getValue()) {
            OperationStats operationStats = aggregation.totalStats.getOperationsStats().get(operation);
            if (operationStats == null) continue;

            String categoryName = entry.getKey().getConfiguration().name;
            if (subCategory != null)
               categoryName = String.format("%s, %s", categoryName, subCategory);

            double subCategoryNumeric;
            String subCategoryValue;
            if (maxIterations > 1) {
               subCategoryNumeric = aggregation.iteration.id;
               subCategoryValue = aggregation.iteration.getValue() != null ? aggregation.iteration.getValue() : String.valueOf(aggregation.iteration.id);
            } else {
               subCategoryNumeric = entry.getKey().getCluster().getSize();
               subCategoryValue = String.format("Size %.0f", subCategoryNumeric);
            }
            switch (statisticType) {
               case MEAN_AND_DEV: {
                  MeanAndDev meanAndDev = operationStats.getRepresentation(MeanAndDev.class);
                  if (meanAndDev == null) continue;
                  chart.addValue(toMillis(meanAndDev.mean), toMillis(meanAndDev.dev), categoryName, subCategoryNumeric, subCategoryValue);
                  break;
               }
               case ACTUAL_THROUGHPUT: {
                  Throughput throughput = operationStats.getRepresentation(Throughput.class,
                        aggregation.totalThreads, aggregation.totalStats.getEnd() - aggregation.totalStats.getBegin());
                  if (throughput == null) continue;
                  chart.addValue(toMillis(throughput.actual), 0, categoryName, subCategoryNumeric, subCategoryValue);
               }
            }
         }
      }
   }

   private double toMillis(double nanos) {
      return nanos / TimeUnit.MILLISECONDS.toNanos(1);
   }

   protected void writeOperation(final String operation, Map<Report, List<Aggregation>> reportAggregationMap) {
      boolean hasPercentiles = configuration.percentiles.length > 0 && hasRepresentation(operation, reportAggregationMap, Percentile.class, configuration.percentiles[0]);
      boolean hasHistograms = hasRepresentation(operation, reportAggregationMap, Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);

      List<String> iterations = new ArrayList<>(maxIterations);
      for (int iteration = 0; iteration < maxIterations; ++iteration) {
         // in fact we shouldn't have different iterations values for iterations with the same id,
         // but it's possible
         Set<String> iterationValues = new HashSet<>();
         for (List<Aggregation> aggregations : reportAggregationMap.values()) {
            if (aggregations != null && iteration < aggregations.size()) {
               Aggregation aggregation = aggregations.get(iteration);
               if (aggregation != null && aggregation.iteration.getValue() != null) {
                  iterationValues.add(aggregation.iteration.test.iterationsName + " = " + aggregation.iteration.getValue());
               }
            }
         }
         iterations.add(concatOrDefault(iterationValues, "iteration " + String.valueOf(iteration)));
      }

      writeOperationHeader(iterations, hasPercentiles, hasHistograms);
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         writeOperationLine(operation, hasPercentiles, hasHistograms, entry.getKey(), entry.getValue());
      }
      write("</table><br>\n");
   }

   private void writeOperationLine(String operation, boolean hasPercentiles, boolean hasHistograms, Report report, List<Aggregation> aggregations) {
      int nodeCount = aggregations.isEmpty() ? 0 : aggregations.get(0).nodeStats.size();

      write("<tr><th style=\"text-align: left; cursor: pointer\" onClick=\"");
      for (int i = 0; i < nodeCount; ++i) {
         write("switch_visibility('e" + (elementCounter + i) + "'); ");
      }
      write(String.format("\">%s</th><th>%s</th>", report.getConfiguration().name, report.getCluster()));

      int iteration = 0;
      for (Aggregation aggregation : aggregations) {
         Statistics statistics = aggregation.totalStats;
         OperationStats operationStats = statistics == null ? null : statistics.getOperationsStats().get(operation);
         long period = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
         writeRepresentations(operationStats, operation, report.getCluster().getClusterIndex(), iteration, "total",
                              aggregation.totalThreads, period, hasPercentiles, hasHistograms, false, aggregation.anySuspect(operation));
         ++iteration;
      }

      write("</tr>\n");
      for (int node = 0; node < nodeCount; ++node) {
         write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th colspan=\"2\" style=\"text-align: right\">node%d</th>", elementCounter++, node));
         for (Aggregation aggregation : aggregations) {
            Statistics statistics = node >= aggregation.nodeStats.size() ? null : aggregation.nodeStats.get(node);

            OperationStats operationStats = null;
            long period = 0;
            if (statistics != null) {
               operationStats = statistics.getOperationsStats().get(operation);
               period = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
            }
            int threads = node >= aggregation.nodeThreads.size() ? 0 : aggregation.nodeThreads.get(node);
            writeRepresentations(operationStats, operation, report.getCluster().getClusterIndex(), iteration, "node" + node,
                                 threads, period, hasPercentiles, hasHistograms, false, aggregation.anySuspect(operation));
         }
         write("</tr>\n");
      }
   }

   private void writeOperationHeader(List<String> iterationValues, boolean hasPercentiles, boolean hasHistograms) {
      write("<table>\n");
      int columns = (hasHistograms ? 6 : 5) + (hasPercentiles ? configuration.percentiles.length : 0);
      if (maxIterations > 1) {
         write("<tr><th colspan=\"2\">&nbsp;</th>");
         for (String iterationValue : iterationValues) {
            write(String.format("<th colspan=\"%d\" style=\"border-left-color: black; border-left-width: 2px;\">%s</th>", columns, iterationValue));
         }
         write("</tr>\n");
      }
      write("<tr><th colspan=\"2\">Configuration</th>\n");
      for (int i = 0; i < maxIterations; ++i) {
         write("<th style=\"text-align: center; border-left-color: black; border-left-width: 2px;\">requests</th>\n");
         write("<th style=\"text-align: center\">errors</th>\n");
         write("<th>mean</td><th>std.dev</th><th>throughput</th>\n");
         if (hasPercentiles) {
            for (double percentile : configuration.percentiles) {
               write("<th>RTM at " + percentile + "%</th>");
            }
         }
         if (hasHistograms) {
            write("<th>histogram</th>\n");
         }
      }
      write("</tr>\n");
   }

   protected static String concatOrDefault(Collection<String> values, String def) {
      if (values.isEmpty()) {
         return def;
      } else {
         StringBuilder sb = new StringBuilder();
         for (String value : values) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(value);
         }
         return sb.toString();
      }
   }

   protected static boolean hasRepresentation(final String operation, Map<Report, List<Aggregation>> reportAggregationMap, final Class<?> representationClass, final Object... representationArgs) {
      return Projections.any(Projections.notNull(reportAggregationMap.values()), new Projections.Condition<List<Aggregation>>() {
         @Override
         public boolean accept(List<Aggregation> aggregations) {
            return Projections.any(Projections.notNull(aggregations), new Projections.Condition<Aggregation>() {
               @Override
               public boolean accept(Aggregation aggregation) {
                  OperationStats operationStats = aggregation.totalStats.getOperationsStats().get(operation);
                  return operationStats != null && operationStats.getRepresentation(representationClass, representationArgs) != null;
               }
            });
         }
      });
   }

   private void writeRepresentations(OperationStats operationStats, String operation, int cluster, int iteration, String node,
                                     int threads, long period, boolean hasPercentiles, boolean hasHistograms, boolean gray, boolean suspect) {
      DefaultOutcome defaultOutcome = operationStats == null ? null : operationStats.getRepresentation(DefaultOutcome.class);
      Throughput throughput = operationStats == null ? null : operationStats.getRepresentation(Throughput.class, threads, period);
      MeanAndDev meanAndDev = operationStats == null ? null : operationStats.getRepresentation(MeanAndDev.class);
      Histogram histogram = operationStats == null ? null : operationStats.getRepresentation(Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);

      String rowStyle = suspect ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; " : "");
      rowStyle += "text-align: right; ";

      writeTD(defaultOutcome == null ? "&nbsp;" : String.valueOf(defaultOutcome.requests),
              rowStyle + "border-left-color: black; border-left-width: 2px;");
      writeTD(defaultOutcome == null ? "&nbsp;" : String.valueOf(defaultOutcome.errors), rowStyle);
      writeTD(meanAndDev == null ? "&nbsp;" : formatTime(meanAndDev.mean), rowStyle);
      writeTD(meanAndDev == null ? "&nbsp;" : formatTime(meanAndDev.dev), rowStyle);
      if (hasPercentiles) {
         for (double percentile : configuration.percentiles) {
            Percentile p = operationStats == null ? null : operationStats.getRepresentation(Percentile.class, percentile);
            writeTD(p == null ? "&nbsp;" : formatTime(p.responseTimeMax), rowStyle);
         }
      }
      writeTD(throughput == null ? "&nbsp;" : String.format("%.0f&nbsp;reqs/s", throughput.actual), rowStyle);
      if (hasHistograms) {
         if (histogram == null) {
            writeTD("none", rowStyle);
         } else {
            String filename = String.format("histogram_%s_%s_%d_%d_%s.png", testName, operation, cluster, iteration, node);
            try {
               HistogramChart chart = new HistogramChart().setData(operation, histogram);
               chart.setWidth(configuration.histogramWidth).setHeight(configuration.histogramHeight);
               chart.save(directory + File.separator + filename);
               writeTD(String.format("<a href=\"%s\">show</a>", filename), rowStyle);
            } catch (Exception e) {
               log.error("Failed to generate chart " + filename, e);
               writeTD("error", rowStyle);
            }
         }
      }
   }

   private String formatTime(double value) {
      return Utils.prettyPrintTime((long) value, TimeUnit.NANOSECONDS).replaceAll(" ", "&nbsp;");
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

   protected abstract ComparisonChart generateChart(int clusterSize, String operation, String rangeAxisLabel, StatisticType statisticType);

   protected static enum StatisticType {
      MEAN_AND_DEV, ACTUAL_THROUGHPUT
   }

   protected static class Configuration {
      @Property(doc = "Generate separate charts for different cluster sizes. Default is false.")
      protected boolean separateClusterCharts = false;

      @Property(doc = "List of test names that should be reported together. Default is empty.")
      protected List<List<String>> combinedTests = Collections.EMPTY_LIST;

      @Property(name = "histogram.buckets", doc = "Number of bars the histogram chart will show. Default is 40.")
      protected int histogramBuckets = 40;

      @Property(name = "histogram.percentile", doc = "Percentage of fastest responses that will be presented in the chart. Default is 99%.")
      protected double histogramPercentile = 99d;

      @Property(name = "histogram.chart.width", doc = "Width of the histogram chart in pixels. Default is 800.")
      protected int histogramWidth = 800;

      @Property(name = "histogram.chart.height", doc = "Height of the histogram chart in pixels. Default is 600.")
      protected int histogramHeight = 600;

      @Property(doc = "Show response time at certain percentiles. Default is 95% and 99%.")
      protected double[] percentiles = new double[] { 95d, 99d };
   }
}
