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
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DataThroughput;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.stats.representation.Percentile;
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
   private ArrayList<Future> chartTaskFutures = new ArrayList<>();

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
      StringBuffer headerString = new StringBuffer("<table>");
      StringBuffer chartString = new StringBuffer();
      if (createChart(
            String.format("%s%s%s_%s%s_mean_dev.png", directory, File.separator, testName, operation, suffix),
            clusterSize, operation, "Response time (ms)", StatisticType.MEAN_AND_DEV)) {
         headerString.append("<th style=\"text-align: center;border: 0px;\">Response time mean</th>");
         chartString.append(String.format(
               "<td style=\"border: 0px;\"><img src=\"%s_%s%s_mean_dev.png\" alt=\"%s\"/></div></div></td>\n",
               testName, operation, suffix, operation));
      }
      if (createChart(
            String.format("%s%s%s_%s%s_throughput.png", directory, File.separator, testName, operation, suffix),
            clusterSize, operation, "Operations/sec", StatisticType.OPERATION_THROUGHPUT)) {
         headerString.append("<th style=\"text-align: center;border: 0px;\">Operation throughput</th>");
         chartString.append(String.format(
               "<td style=\"border: 0px;\"><img src=\"%s_%s%s_throughput.png\" alt=\"%s\"/></div></div>\n", testName,
               operation, suffix, operation));
      }
      if (createChart(
            String.format("%s%s%s_%s%s_data_throughput.png", directory, File.separator, testName, operation, suffix),
            clusterSize, operation, "MB/sec", StatisticType.DATA_THROUGHPUT)) {
         headerString.append("<th style=\"text-align: center;border: 0px;\">Data throughput mean</th>");
         chartString.append(String.format(
               "<td style=\"border: 0px;\"><img src=\"%s_%s%s_data_throughput.png\" alt=\"%s\"/></div></div>\n",
               testName, operation, suffix, operation));
      }
      headerString.append("<tr>");
      write(headerString.toString());
      write(chartString.toString());
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
         if (configuration.generateNodeStats) {
            for (int node = 0; node < nodeCount; ++node) {
               write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th colspan=\"2\" style=\"text-align: right\">node%d</th>", elementCounter++, node));
               for (Report.TestResult result : entry.getValue()) {
                  Report.SlaveResult sr = result.slaveResults.get(node);
                  writeResult(sr.value, false, sr.suspicious);
               }
               write("</tr>\n");
            }
         }
      }
      write("</table><br>\n");
   }

   private void writeResult(String value, boolean gray, boolean suspect) {
      String rowStyle = suspect && configuration.highlightSuspects ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; " : "");
      rowStyle += "text-align: right; ";
      writeTD(value, rowStyle + "border-left-color: black; border-left-width: 2px;");
   }

   protected boolean createChart(String filename, int clusterSize, String operation, String rangeAxisLabel,
         StatisticType statisticType) throws IOException {
      ComparisonChart chart = generateChart(clusterSize, operation, rangeAxisLabel, statisticType);
      if (chart != null) {
         chart.setWidth(Math.min(Math.max(maxConfigurations, maxIterations) * 100 + 200, 1800));
         chart.setHeight(Math.min(maxConfigurations * 100 + 200, 800));
         chart.save(filename);
         return true;
      }
      return false;
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

   protected boolean addToChart(ComparisonChart chart, String subCategory, String operation, StatisticType statisticType,
         Map<Report, List<Aggregation>> reportAggregationMap) {
      Map<String, List<Report>> byConfiguration = Projections.groupBy(reportAggregationMap.keySet(), new Projections.Func<Report, String>() {
         @Override
         public String project(Report report) {
            return report.getConfiguration().name;
         }
      });
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         for (Aggregation aggregation : entry.getValue()) {
            OperationStats operationStats = aggregation.totalStats.getOperationsStats().get(operation);
            if (operationStats == null)
               return false;

            String categoryName = entry.getKey().getConfiguration().name;
            if (subCategory != null) {
               categoryName = String.format("%s, %s", categoryName, subCategory);
            }
            // if there are multiple reports for the same configuration (multiple clusters), use cluster size in category
            if (byConfiguration.get(entry.getKey().getConfiguration().name).size() > 1) {
               categoryName = String.format("%s, size %d", categoryName, entry.getKey().getCluster().getSize());
            }

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
               if (meanAndDev == null) return false;
               chart.addValue(toMillis(meanAndDev.mean), toMillis(meanAndDev.dev), categoryName, subCategoryNumeric,
                     subCategoryValue);
               break;
            }
            case OPERATION_THROUGHPUT: {
               OperationThroughput throughput = operationStats.getRepresentation(OperationThroughput.class,
                     aggregation.totalThreads, TimeUnit.MILLISECONDS.toNanos(aggregation.totalStats.getEnd() - aggregation.totalStats.getBegin()));
               if (throughput == null) return false;
               chart.addValue(throughput.actual, 0, categoryName, subCategoryNumeric, subCategoryValue);
               break;
            }
            case DATA_THROUGHPUT: {
               DataThroughput dataThroughput = operationStats.getRepresentation(DataThroughput.class,
                     aggregation.totalThreads, aggregation.totalStats.getEnd() - aggregation.totalStats.getBegin());
               if (dataThroughput == null) return false;
               chart.addValue(dataThroughput.meanThroughput / (1024.0 * 1024.0), dataThroughput.deviation
                     / (1024.0 * 1024.0), categoryName, subCategoryNumeric, subCategoryValue);
               break;
            }
            }
         }
      }
      return true;
   }

   private double toMillis(double nanos) {
      return nanos / TimeUnit.MILLISECONDS.toNanos(1);
   }

   protected void writeOperation(final String operation, Map<Report, List<Aggregation>> reportAggregationMap, String singleTestName) {
      Collection<StatisticType> presentedStatistics = new ArrayList<>();
      presentedStatistics.add(StatisticType.MEAN_AND_DEV);
      if (configuration.percentiles.length > 0 && hasRepresentation(operation, reportAggregationMap, Percentile.class, configuration.percentiles[0])) {
         presentedStatistics.add(StatisticType.PERCENTILES);
      }
      if (hasRepresentation(operation, reportAggregationMap, Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile)) {
         presentedStatistics.add(StatisticType.HISTOGRAM);
      }
      if (hasRepresentation(operation, reportAggregationMap, OperationThroughput.class, 100, 100L)) {
         presentedStatistics.add(StatisticType.OPERATION_THROUGHPUT);
      }
      if (hasRepresentation(operation, reportAggregationMap, DataThroughput.class, 100L, 100L, 100L, 100.0)) {
         presentedStatistics.add(StatisticType.DATA_THROUGHPUT);
      }

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

      writeOperationHeader(iterations, presentedStatistics, singleTestName);
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         writeOperationLine(operation, presentedStatistics, entry.getKey(), entry.getValue());
      }
      write("</table><br>\n");

      for (Future f : chartTaskFutures) {
         try {
            f.get();
         } catch (Exception e) {
            log.error("Failed to create chart", e);
         }
      }
      chartTaskFutures.clear();
   }

   private void writeOperationLine(String operation, Collection<StatisticType> presentedStatistics, Report report, List<Aggregation> aggregations) {
      int nodeCount = aggregations.isEmpty() ? 0 : aggregations.get(0).nodeStats.size();
      int expandableRows = 0;
      if (configuration.generateNodeStats) {
         expandableRows += nodeCount;
         if (configuration.generateThreadStats) {
            for (int node = 0; node < nodeCount; ++node) {
               expandableRows += getMaxThreads(aggregations, node);
            }
         }
      }

      write("<tr><th style=\"text-align: left; cursor: pointer\" onClick=\"");
      for (int i = 0; i < expandableRows; ++i) {
         write("switch_visibility('e" + (elementCounter + i) + "'); ");
      }
      write(String.format("\">%s</th><th>%s</th>", report.getConfiguration().name, report.getCluster()));


      for (Aggregation aggregation : aggregations) {
         writeRepresentations(aggregation.totalStats, operation, report.getConfiguration().name,
               report.getCluster().getClusterIndex(), aggregation.iteration.id, "total",
               aggregation.totalThreads, presentedStatistics, false, aggregation.anySuspect(operation));
      }

      write("</tr>\n");
      if (configuration.generateNodeStats) {
         for (int node = 0; node < nodeCount; ++node) {
            write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th colspan=\"2\" style=\"text-align: right\">node%d</th>", elementCounter++, node));
            for (Aggregation aggregation : aggregations) {
               Statistics statistics = node >= aggregation.nodeStats.size() ? null : aggregation.nodeStats.get(node);
               int threads = node >= aggregation.nodeThreads.size() ? 0 : aggregation.nodeThreads.get(node);
               writeRepresentations(statistics, operation, report.getConfiguration().name,
                     report.getCluster().getClusterIndex(), aggregation.iteration.id, "node" + node,
                     threads, presentedStatistics, false, aggregation.anySuspect(operation));
            }
            write("</tr>\n");
            if (configuration.generateThreadStats) {
               int maxThreads = getMaxThreads(aggregations, node);
               for (int thread = 0; thread < maxThreads; ++thread) {
                  write(String.format("<tr id=\"e%d\" style=\"visibility: collapse;\"><th colspan=\"2\" style=\"text-align: right\">thread %d_%d</th>", elementCounter++, node, thread));
                  for (Aggregation aggregation : aggregations) {
                     List<Statistics> nodeStats = aggregation.iteration.getStatistics(node);
                     Statistics threadStats = nodeStats == null || nodeStats.size() <= thread ? null : nodeStats.get(thread);
                     writeRepresentations(threadStats, operation, report.getConfiguration().name,
                           report.getCluster().getClusterIndex(), aggregation.iteration.id, "thread" + node + "_" + thread,
                           1, presentedStatistics, false, aggregation.anySuspect(operation));
                  }
                  write("</tr>\n");
               }
            }
         }
      }
   }

   private int getMaxThreads(List<Aggregation> aggregations, final int slaveIndex) {
      Integer maxThreads = Projections.max(Projections.project(aggregations, new Projections.Func<Aggregation, Integer>() {
         @Override
         public Integer project(Aggregation aggregation) {
            List<Statistics> statistics = aggregation.iteration.getStatistics(slaveIndex);
            return statistics == null ? 0 : statistics.size();
         }
      }));
      return maxThreads != null ? maxThreads : 0;
   }

   private void writeOperationHeader(List<String> iterationValues, Collection<StatisticType> presentedStatistics, String singleTestName) {
      write("<table>\n");
      int columns = 4;
      columns += presentedStatistics.contains(StatisticType.HISTOGRAM) ? 1 : 0;
      columns += presentedStatistics.contains(StatisticType.PERCENTILES) ? configuration.percentiles.length : 0;
      columns += presentedStatistics.contains(StatisticType.OPERATION_THROUGHPUT) ? 1 : 0;
      columns += presentedStatistics.contains(StatisticType.DATA_THROUGHPUT) ? 4 : 0;

      if (maxIterations > 1) {
         write("<tr><th colspan=\"2\">&nbsp;</th>");
         for (String iterationValue : iterationValues) {
            write(String.format("<th colspan=\"%d\" style=\"border-left-color: black; border-left-width: 2px;\">%s</th>", columns, iterationValue));
         }
         write("</tr>\n");
      }
      write(String.format("<tr><th colspan=\"2\">Configuration %s</th>\n", singleTestName));
      for (int i = 0; i < maxIterations; ++i) {
         write("<th style=\"text-align: center; border-left-color: black; border-left-width: 2px;\">requests</th>\n");
         write("<th style=\"text-align: center\">errors</th>\n");
         write("<th>mean</td><th>std.dev</th>\n");
         if (presentedStatistics.contains(StatisticType.OPERATION_THROUGHPUT)){
            write("<th>operation throughput</th>\n");
         }
         if (presentedStatistics.contains(StatisticType.DATA_THROUGHPUT)){
            write("<th colspan=\"4\">data throughput</th>\n");
         }
         if (presentedStatistics.contains(StatisticType.PERCENTILES)) {
            for (double percentile : configuration.percentiles) {
               write("<th>RTM at " + percentile + "%</th>");
            }
         }
         if (presentedStatistics.contains(StatisticType.HISTOGRAM)) {
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

   private void writeRepresentations(Statistics statistics, final String operation, String configurationName, int cluster, int iteration,
                                     String node, int threads, Collection<StatisticType> presentedStatistics, boolean gray, boolean suspect) {
      OperationStats operationStats = null;
      long period = 0;
      if (statistics != null) {
         operationStats = statistics.getOperationsStats().get(operation);
         period = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
      }

      DefaultOutcome defaultOutcome = operationStats == null ? null : operationStats.getRepresentation(DefaultOutcome.class);
      MeanAndDev meanAndDev = operationStats == null ? null : operationStats.getRepresentation(MeanAndDev.class);

      String rowStyle = suspect && configuration.highlightSuspects ? "background-color: #FFBBBB; " : (gray ? "background-color: #F0F0F0; " : "");
      rowStyle += "text-align: right; ";

      String firstCellStyle = rowStyle + "border-left-color: black; border-left-width: 2px;";
      if (defaultOutcome != null) {
         writeTD(String.valueOf(defaultOutcome.requests), firstCellStyle);
         writeTD(String.valueOf(defaultOutcome.errors), rowStyle);
      } else {
         writeEmptyTDs(1, firstCellStyle);
         writeEmptyTDs(1, rowStyle);
      }
      if (presentedStatistics.contains(StatisticType.MEAN_AND_DEV)) {
         if (meanAndDev != null) {
            writeTD(formatTime(meanAndDev.mean), rowStyle);
            writeTD(formatTime(meanAndDev.dev), rowStyle);
         } else {
            writeEmptyTDs(2, rowStyle);
         }
      }
      if (presentedStatistics.contains(StatisticType.OPERATION_THROUGHPUT)) {
         OperationThroughput operationThroughput = operationStats == null ? null : operationStats.getRepresentation(OperationThroughput.class, threads, period);
         if (operationThroughput != null) {
            writeTD(String.format("%.0f&nbsp;reqs/s", operationThroughput.actual), rowStyle);
         } else {
            writeTD("&nbsp;", rowStyle);
         }
      }
      if (presentedStatistics.contains(StatisticType.DATA_THROUGHPUT)) {
         DataThroughput dataThroughput = operationStats == null ? null : operationStats.getRepresentation(DataThroughput.class);
         if (dataThroughput != null) {
            writeTD(String.format("%.0f&nbsp;MB/s - min", dataThroughput.minThroughput / (1024.0 * 1024.0)), rowStyle);
            writeTD(String.format("%.0f&nbsp;MB/s - max", dataThroughput.maxThroughput / (1024.0 * 1024.0)), rowStyle);
            writeTD(String.format("%.0f&nbsp;MB/s - mean", dataThroughput.meanThroughput / (1024.0 * 1024.0)), rowStyle);
            writeTD(String.format("%.0f&nbsp;MB/s - std. dev", dataThroughput.deviation / (1024.0 * 1024.0)), rowStyle);
         } else {
            writeEmptyTDs(4, rowStyle);
         }
      }
      if (presentedStatistics.contains(StatisticType.PERCENTILES)) {
         for (double percentile : configuration.percentiles) {
            Percentile p = operationStats == null ? null : operationStats.getRepresentation(Percentile.class, percentile);
            writeTD(p == null ? "&nbsp;" : formatTime(p.responseTimeMax), rowStyle);
         }
      }
      if (presentedStatistics.contains(StatisticType.HISTOGRAM)) {
         final Histogram histogram = operationStats == null ? null : operationStats.getRepresentation(Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);
         if (histogram == null) {
            writeTD("none", rowStyle);
         } else {
            final String filename = String.format("histogram_%s_%s_%s_%d_%d_%s.png", testName, operation, configurationName, cluster, iteration, node);
            chartTaskFutures.add(HtmlReporter.executor.submit(new Callable<Void>() {
               @Override
               public Void call() throws Exception {
                  log.debug("Generating histogram " + filename);
                  HistogramChart chart = new HistogramChart().setData(operation, histogram);
                  chart.setWidth(configuration.histogramWidth).setHeight(configuration.histogramHeight);
                  chart.save(directory + File.separator + filename);
                  return null;
               }
            }));
            writeTD(String.format("<a href=\"%s\">show</a>", filename), rowStyle);
         }
      }
   }

   private void writeEmptyTDs(int cells, String rowStyle) {
      for (int i = 0; i < cells; ++i) writeTD("&nbsp;", rowStyle);
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
      MEAN_AND_DEV, OPERATION_THROUGHPUT, DATA_THROUGHPUT, HISTOGRAM, PERCENTILES
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

      @Property(doc = "Generate statistics for each node (expandable menu). Default is true.")
      protected boolean generateNodeStats = true;

      @Property(doc = "Generate statistics for each thread (expandable menu). Default is false.")
      protected boolean generateThreadStats = false;

      @Property(doc = "Highlight suspicious results in the report. Default is true.")
      protected boolean highlightSuspects = true;
   }
}
