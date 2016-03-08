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
   private List<Future> chartTaskFutures = new ArrayList<>();
   private List<String> generatedCharts = new ArrayList<>();

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

   public String createHistogramAndPercentileChart(Statistics statistics, final String operation, final String configurationName, int cluster, int iteration,
                                                   String node, Collection<StatisticType> presentedStatistics) {

      OperationStats operationStats = null;
      if (statistics != null) {
         operationStats = statistics.getOperationsStats().get(operation);
      }

      String resultFileName = "";
      if (presentedStatistics.contains(StatisticType.HISTOGRAM)) {
         final Histogram histogram = operationStats == null ? null : operationStats.getRepresentation(Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);
         if (histogram != null) {
            final String histogramFilename = getHistogramName(operationStats, operation, configurationName, cluster, iteration, node, presentedStatistics);
            chartTaskFutures.add(HtmlReporter.executor.submit(new Callable<Void>() {
               @Override
               public Void call() throws Exception {
                  log.debug("Generating histogram " + histogramFilename);
                  HistogramChart chart = new HistogramChart().setData(operation, histogram);
                  chart.setWidth(configuration.histogramWidth).setHeight(configuration.histogramHeight);
                  chart.save(directory + File.separator + histogramFilename);
                  return null;
               }
            }));

            final Histogram fullHistogram = operationStats.getRepresentation(Histogram.class);
            final String percentilesFilename = getPercentileChartName(operationStats, operation, configurationName, cluster, iteration, node, presentedStatistics);
            chartTaskFutures.add(HtmlReporter.executor.submit(new Callable<Void>() {
               @Override
               public Void call() throws Exception {
                  log.debug("Generating percentiles " + percentilesFilename);
                  PercentilesChart chart = new PercentilesChart().addSeries(configurationName, fullHistogram);
                  chart.setWidth(configuration.histogramWidth).setHeight(configuration.histogramHeight);
                  chart.save(directory + File.separator + percentilesFilename);
                  return null;
               }
            }));
         }
      }
      return resultFileName;
   }

   protected boolean createChart(String filename, int clusterSize, String operation, String rangeAxisLabel,
                                 ChartType chartType) throws IOException {
      ComparisonChart chart = generateChart(clusterSize, operation, rangeAxisLabel, chartType);
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

   protected boolean addToChart(ComparisonChart chart, String subCategory, String operation, ChartType chartType,
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
            switch (chartType) {
               case MEAN_AND_DEV: {
                  MeanAndDev meanAndDev = operationStats.getRepresentation(MeanAndDev.class);
                  if (meanAndDev == null) return false;
                  chart.addValue(toMillis(meanAndDev.mean), toMillis(meanAndDev.dev), categoryName, subCategoryNumeric,
                     subCategoryValue);
                  break;
               }
               case OPERATION_THROUGHPUT_GROSS: {
                  OperationThroughput throughput = operationStats.getRepresentation(OperationThroughput.class,
                     TimeUnit.MILLISECONDS.toNanos(aggregation.totalStats.getEnd() - aggregation.totalStats.getBegin()));
                  if (throughput == null) return false;
                  chart.addValue(throughput.gross, 0, categoryName, subCategoryNumeric, subCategoryValue);
                  break;
               }
               case OPERATION_THROUGHPUT_NET: {
                  OperationThroughput throughput = operationStats.getRepresentation(OperationThroughput.class,
                     TimeUnit.MILLISECONDS.toNanos(aggregation.totalStats.getEnd() - aggregation.totalStats.getBegin()));
                  if (throughput == null) return false;
                  chart.addValue(throughput.net, 0, categoryName, subCategoryNumeric, subCategoryValue);
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

   public int getMaxThreads(List<Aggregation> aggregations, final int slaveIndex) {
      Integer maxThreads = Projections.max(Projections.project(aggregations, new Projections.Func<Aggregation, Integer>() {
         @Override
         public Integer project(Aggregation aggregation) {
            List<Statistics> statistics = aggregation.iteration.getStatistics(slaveIndex);
            return statistics == null ? 0 : statistics.size();
         }
      }));
      return maxThreads != null ? maxThreads : 0;
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

   public void createCharts(String operation, int clusterSize) throws IOException {
      String suffix = clusterSize > 0 ? "_" + clusterSize : "";
      String directory = this.directory.endsWith(File.separator) ? this.directory : this.directory + File.separator;

      if (createChart(
         String.format("%s%s%s_%s%s_mean_dev.png", directory, File.separator, testName, operation, suffix),
         clusterSize, operation, "Response time (ms)", ChartType.MEAN_AND_DEV))
         generatedCharts.add("mean_dev");
      if (createChart(
         String.format("%s%s%s_%s%s_throughput_gross.png", directory, File.separator, testName, operation, suffix),
         clusterSize, operation, "Operations/sec", ChartType.OPERATION_THROUGHPUT_GROSS))
         generatedCharts.add("throughput_gross");
      if (createChart(
         String.format("%s%s%s_%s%s_throughput_net.png", directory, File.separator, testName, operation, suffix),
         clusterSize, operation, "Operations/sec", ChartType.OPERATION_THROUGHPUT_NET))
         generatedCharts.add("throughput_net");
      if (createChart(
         String.format("%s%s%s_%s%s_data_throughput.png", directory, File.separator, testName, operation, suffix),
         clusterSize, operation, "MB/sec", ChartType.DATA_THROUGHPUT))
         generatedCharts.add("data_throughput");
   }

   // generating Histogram and Percentile Graphs
   protected void createHistogramAndPercentileCharts(final String operation, Map<Report, List<Aggregation>> reportAggregationMap, String singleTestName) {
      Collection<StatisticType> presentedStatistics = new ArrayList<>();

      if (hasRepresentation(operation, reportAggregationMap, Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile)) {
         presentedStatistics.add(StatisticType.HISTOGRAM);
      }

      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         createSingleHistogramAndPercentileCharts(operation, presentedStatistics, entry.getKey(), entry.getValue());
      }
   }

   private void createSingleHistogramAndPercentileCharts(String operation, Collection<StatisticType> presentedStatistics, Report report, List<Aggregation> aggregations) {
      int nodeCount = aggregations.isEmpty() ? 0 : aggregations.get(0).nodeStats.size();

      for (Aggregation aggregation : aggregations) {
         createHistogramAndPercentileChart(aggregation.totalStats, operation, report.getConfiguration().name, report.getCluster().getClusterIndex(),
            aggregation.iteration.id, "total", presentedStatistics);
      }

      if (configuration.generateNodeStats) {
         for (int node = 0; node < nodeCount; ++node) {
            for (Aggregation aggregation : aggregations) {
               Statistics statistics = node >= aggregation.nodeStats.size() ? null : aggregation.nodeStats.get(node);

               createHistogramAndPercentileChart(statistics, operation, report.getConfiguration().name, report.getCluster().getClusterIndex(),
                  aggregation.iteration.id, "node" + node, presentedStatistics);
            }
            if (configuration.generateThreadStats) {
               int maxThreads = getMaxThreads(aggregations, node);
               for (int thread = 0; thread < maxThreads; ++thread) {
                  for (Aggregation aggregation : aggregations) {
                     List<Statistics> nodeStats = aggregation.iteration.getStatistics(node);
                     Statistics threadStats = nodeStats == null || nodeStats.size() <= thread ? null : nodeStats.get(thread);

                     createHistogramAndPercentileChart(threadStats, operation, report.getConfiguration().name, report.getCluster().getClusterIndex(),
                        aggregation.iteration.id, "thread" + node + "_" + thread, presentedStatistics);
                  }
               }
            }
         }
      }
   }

   protected void waitForChartsGeneration() {
      for (Future f : chartTaskFutures) {
         try {
            f.get();
         } catch (Exception e) {
            log.error("Failed to create chart", e);
         }
      }
      chartTaskFutures.clear();
   }

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */
   public String getTestName() {
      return testName;
   }

   public int getMaxClusters() {
      return maxClusters;
   }

   public Configuration getConfiguration() {
      return configuration;
   }

   public int getMaxIterations() {
      return maxIterations;
   }

   public int getElementCounter() {
      return elementCounter;
   }

   public void incElementCounter() {
      this.elementCounter++;
   }

   public Class defaultOutcomeClass() {
      return DefaultOutcome.class;
   }

   public Class meanAndDevClass() {
      return MeanAndDev.class;
   }

   public Class operationThroughputClass() {
      return OperationThroughput.class;
   }

   public Class dataThroughputClass() {
      return DataThroughput.class;
   }

   public Class percentileClass() {
      return Percentile.class;
   }

   public String formatTime(double value) {
      return Utils.prettyPrintTime((long) value, TimeUnit.NANOSECONDS).replaceAll(" ", "&nbsp;");
   }

   //These methods are used in templates
   public String generateImageName(String operation, String suffix, String name) {
      return String.format("%s_%s%s_%s\"", testName, operation, suffix, name);

   }

   public List<String> getGeneratedCharts() {
      return generatedCharts;
   }

   public String getHistogramName(OperationStats operationStats, final String operation, String configurationName, int cluster, int iteration,
                                  String node, Collection<StatisticType> presentedStatistics) {
      String resultFileName = "";
      if (presentedStatistics.contains(StatisticType.HISTOGRAM)) {
         final Histogram histogram = operationStats == null ? null : operationStats.getRepresentation(Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);
         if (histogram == null) {
            return resultFileName;
         } else {
            resultFileName = String.format("histogram_%s_%s_%s_%d_%d_%s.png", testName, operation, configurationName, cluster, iteration, node);
         }
      }
      return resultFileName;
   }

   public String getPercentileChartName(OperationStats operationStats, final String operation, String configurationName, int cluster, int iteration,
                                        String node, Collection<StatisticType> presentedStatistics) {
      String resultFileName = "";
      if (presentedStatistics.contains(StatisticType.HISTOGRAM)) {
         final Histogram histogram = operationStats == null ? null : operationStats.getRepresentation(Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);
         if (histogram == null) {
            return resultFileName;
         } else {
            resultFileName = String.format("percentiles_%s_%s_%s_%d_%d_%s.png", testName, operation, configurationName, cluster, iteration, node);
         }
      }
      return resultFileName;
   }

   public OperationStats operationStats(Statistics statistics, String operation) {
      OperationStats operationStats = null;
      if (statistics != null) {
         operationStats = statistics.getOperationsStats().get(operation);
      }
      return operationStats;
   }

   public long period(Statistics statistics) {
      long period = 0;
      if (statistics != null) {
         period = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
      }
      return period;
   }

   public String rowClass(boolean suspect) {
      String rowClass = suspect && configuration.highlightSuspects ? "highlight" : "";
      return rowClass;
   }

   public String formatOperationThroughput(double operationThroughput) {
      return String.format("%.0f&nbsp;reqs/s", operationThroughput);
   }

   protected abstract ComparisonChart generateChart(int clusterSize, String operation, String rangeAxisLabel, ChartType chartType);

   public String formatDataThroughput(double value) {
      return String.format("%.0f&nbsp;MB/s ", value / (1024.0 * 1024.0));
   }

   public int numberOfColumns(Collection<StatisticType> presentedStatistics) {
      int columns = 4;
      columns += presentedStatistics.contains(StatisticType.HISTOGRAM) ? 1 : 0;
      columns += presentedStatistics.contains(StatisticType.PERCENTILES) ? configuration.percentiles.length : 0;
      columns += presentedStatistics.contains(StatisticType.OPERATION_THROUGHPUT) ? 2 : 0;
      columns += presentedStatistics.contains(StatisticType.DATA_THROUGHPUT) ? 4 : 0;

      return columns;
   }

   public int calculateExpandableRows(List<Aggregation> aggregations, int nodeCount) {
      int expandableRows = 0;
      if (configuration.generateNodeStats) {
         expandableRows += nodeCount;
         if (configuration.generateThreadStats) {
            for (int node = 0; node < nodeCount; ++node) {
               expandableRows += getMaxThreads(aggregations, node);
            }
         }
      }
      return expandableRows;
   }

   public Statistics getStatistics(Aggregation aggregation, int node) {
      Statistics statistics = node >= aggregation.nodeStats.size() ? null : aggregation.nodeStats.get(node);
      return statistics;
   }

   public Statistics getThreadStatistics(Aggregation aggregation, int node, int thread) {
      List<Statistics> nodeStats = aggregation.iteration.getStatistics(node);
      Statistics threadStats = nodeStats == null || nodeStats.size() <= thread ? null : nodeStats.get(thread);
      return threadStats;
   }

   public int getThreads(Aggregation aggregation, int node) {
      int threads = node >= aggregation.nodeThreads.size() ? 0 : aggregation.nodeThreads.get(node);
      return threads;
   }


   public boolean separateClusterCharts() {
      return configuration.separateClusterCharts;
   }

   public OperationData getOperationData(final String operation, Map<Report, List<Aggregation>> reportAggregationMap) {

      Collection<StatisticType> presentedStatistics = new ArrayList<>();

      presentedStatistics.add(StatisticType.MEAN_AND_DEV);
      if (configuration.percentiles.length > 0 && hasRepresentation(operation, reportAggregationMap, Percentile.class, configuration.percentiles[0])) {
         presentedStatistics.add(StatisticType.PERCENTILES);
      }
      if (hasRepresentation(operation, reportAggregationMap, Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile)) {
         presentedStatistics.add(StatisticType.HISTOGRAM);
      }
      if (hasRepresentation(operation, reportAggregationMap, OperationThroughput.class, 1L)) {
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

      return new OperationData(presentedStatistics, iterations);
   }

   //helper class for FreeMarker template
   public class OperationData {
      private List<String> iterationValues;
      private Collection<StatisticType> presentedStatistics;

      public OperationData(Collection<StatisticType> presentedStatistics, List<String> iterationValues) {
         this.presentedStatistics = presentedStatistics;
         this.iterationValues = iterationValues;
      }

      public List<String> getIterationValues() {
         return iterationValues;
      }

      public Collection<StatisticType> getPresentedStatistics() {
         return presentedStatistics;
      }
   }

   protected enum StatisticType {
      MEAN_AND_DEV, OPERATION_THROUGHPUT, DATA_THROUGHPUT, HISTOGRAM, PERCENTILES
   }

   protected enum ChartType {
      MEAN_AND_DEV, OPERATION_THROUGHPUT_NET, OPERATION_THROUGHPUT_GROSS, DATA_THROUGHPUT
   }

   public static class Configuration {
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
      protected double[] percentiles = new double[] {95d, 99d};

      @Property(doc = "Generate statistics for each node (expandable menu). Default is true.")
      protected boolean generateNodeStats = true;

      @Property(doc = "Generate statistics for each thread (expandable menu). Default is false.")
      protected boolean generateThreadStats = false;

      @Property(doc = "Highlight suspicious results in the report. Default is true.")
      protected boolean highlightSuspects = true;

      /**
       * The following methods are used in Freemarker templates
       * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
       */

      public int getHistogramBuckets() {
         return histogramBuckets;
      }

      public double getHistogramPercentile() {
         return histogramPercentile;
      }

      public int getHistogramWidth() {
         return histogramWidth;
      }

      public int getHistogramHeight() {
         return histogramHeight;
      }

      public double[] getPercentiles() {
         return percentiles;
      }

      public boolean getGenerateNodeStats() {
         return generateNodeStats;
      }

      public boolean getGenerateThreadStats() {
         return generateThreadStats;
      }

      public boolean getHighlightSuspects() {
         return highlightSuspects;
      }

      public boolean getSeparateClusterCharts() {
         return separateClusterCharts;
      }

      public List<List<String>> getCombinedTests() {
         return combinedTests;
      }
   }
}
