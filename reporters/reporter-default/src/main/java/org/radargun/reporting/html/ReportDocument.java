package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DataThroughput;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.Histogram;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.stats.representation.Percentile;
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
   private Map<String, List<ChartDescription>> generatedCharts = new HashMap<>();

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

   public void createHistogramAndPercentileChart(Statistics statistics, final String operation, final String configurationName, int cluster, int iteration,
                                                   String node, Collection<StatisticType> presentedStatistics) {

      if (!presentedStatistics.contains(StatisticType.HISTOGRAM)) {
         return;
      }
      final Histogram histogram = statistics.getRepresentation(operation, Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);
      if (histogram == null) {
         return;
      }
      if (!LongStream.of(histogram.counts).anyMatch(count -> count > 0)) {
         return;
      }
      final String histogramFilename = getHistogramName(statistics, operation, configurationName, cluster, iteration, node, presentedStatistics);
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

      final Histogram fullHistogram = statistics.getRepresentation(operation, Histogram.class);
      final String percentilesFilename = getPercentileChartName(statistics, operation, configurationName, cluster, iteration, node, presentedStatistics);
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

   protected ComparisonChart createComparisonChart(String iterationsName, String rangeAxisLabel, ChartType chartType) {
      ComparisonChart chart;
      // We've simplified the rule: when we have more iterations, it's always line chart,
      // with tests/sizes included in the categoryName and iterations on domain axis.
      // When there's only one iteration, we put cluster sizes on domain axis but use bar chart.
      if (maxIterations > 1 || chartType.requiresLineChart) {
         chart = new LineChart(iterationsName != null ? iterationsName : chartType.defaultDomainLabel, rangeAxisLabel);
      } else {
         chart = new BarChart("Cluster size", rangeAxisLabel);
      }
      return chart;
   }

   protected boolean addToChart(ComparisonChart chart, String subCategory, String operation, ChartType chartType,
                                Map<Report, List<Aggregation>> reportAggregationMap) {
      Map<String, List<Report>> byConfiguration = reportAggregationMap.keySet().stream().collect(Collectors.groupingBy(report -> report.getConfiguration().name));
      for (Map.Entry<Report, List<Aggregation>> entry : reportAggregationMap.entrySet()) {
         for (Aggregation aggregation : entry.getValue()) {
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
            String seriesCategoryName;
            if (maxIterations > 1) {
               subCategoryNumeric = aggregation.iteration.id;
               subCategoryValue = aggregation.iteration.getValue() != null ? aggregation.iteration.getValue() : String.valueOf(aggregation.iteration.id);
               seriesCategoryName = categoryName + ", Iteration " + subCategoryValue;
            } else {
               subCategoryNumeric = entry.getKey().getCluster().getSize();
               subCategoryValue = String.format("Size %.0f", subCategoryNumeric);
               seriesCategoryName = categoryName;
            }
            switch (chartType) {
               case MEAN_AND_DEV: {
                  MeanAndDev meanAndDev = aggregation.totalStats.getRepresentation(operation, MeanAndDev.class);
                  if (meanAndDev == null) return false;
                  chart.addValue(toMillis(meanAndDev.mean), toMillis(meanAndDev.dev), categoryName, subCategoryNumeric,
                     subCategoryValue);
                  break;
               }
               case OPERATION_THROUGHPUT_NET: {
                  OperationThroughput throughput = aggregation.totalStats.getRepresentation(operation, OperationThroughput.class);
                  if (throughput == null) return false;
                  chart.addValue(throughput.net, 0, categoryName, subCategoryNumeric, subCategoryValue);
                  break;
               }
               case DATA_THROUGHPUT: {
                  DataThroughput dataThroughput = aggregation.totalStats.getRepresentation(operation, DataThroughput.class);
                  if (dataThroughput == null) return false;
                  chart.addValue(dataThroughput.meanThroughput / (1024.0 * 1024.0), dataThroughput.deviation
                     / (1024.0 * 1024.0), categoryName, subCategoryNumeric, subCategoryValue);
                  break;
               }
               case MEAN_AND_DEV_SERIES: {
                  MeanAndDev.Series series = aggregation.totalStats.getRepresentation(operation, MeanAndDev.Series.class);
                  if (series == null) return false;
                  int sample = 0;
                  for (MeanAndDev meanAndDev : series.samples) {
                     chart.addValue(toMillis(meanAndDev.mean), toMillis(meanAndDev.dev), seriesCategoryName, sample++,
                        String.valueOf(TimeUnit.MILLISECONDS.toSeconds(sample * series.period)));
                  }
                  break;
               }
               case REQUESTS_SERIES: {
                  DefaultOutcome.Series series = aggregation.totalStats.getRepresentation(operation, DefaultOutcome.Series.class);
                  if (series == null) return false;
                  int sample = 0;
                  for (DefaultOutcome defaultOutcome : series.samples) {
                     chart.addValue(defaultOutcome.requests, 0, seriesCategoryName, sample++,
                        String.valueOf(TimeUnit.MILLISECONDS.toSeconds(sample * series.period)));
                  }
                  break;
               }
               case OPERATION_THROUGHPUT_GROSS_SERIES: {
                  OperationThroughput.Series series = aggregation.totalStats.getRepresentation(operation, OperationThroughput.Series.class);
                  if (series == null) return false;
                  int sample = 0;
                  for (OperationThroughput defaultOutcome : series.samples) {
                     chart.addValue(defaultOutcome.gross, 0, seriesCategoryName, sample++,
                        String.valueOf(TimeUnit.MILLISECONDS.toSeconds(sample * series.period)));
                  }
                  break;
               }
               case OPERATION_THROUGHPUT_NET_SERIES: {
                  OperationThroughput.Series series = aggregation.totalStats.getRepresentation(operation, OperationThroughput.Series.class);
                  if (series == null) return false;
                  int sample = 0;
                  for (OperationThroughput defaultOutcome : series.samples) {
                     chart.addValue(defaultOutcome.net, 0, seriesCategoryName, sample++,
                        String.valueOf(TimeUnit.MILLISECONDS.toSeconds(sample * series.period)));
                  }
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
      return aggregations.stream().map(aggregation -> {
         List<Statistics> statistics = aggregation.iteration.getStatistics(slaveIndex);
         return statistics == null ? 0 : statistics.size();
      }).max(Integer::max).orElse(0);
   }

   protected static Collector<String, StringBuilder, String> concatOrDefault(String def) {
      return Collector.of(StringBuilder::new,
         (sb, s) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
         },
         (sb1, sb2) -> sb1.length() > 0 ? sb1.append(", ").append(sb2.toString()) : sb2,
         sb -> sb.length() == 0 ? def : sb.toString()
      );
   }

   protected static boolean hasRepresentation(final String operation, Map<Report, List<Aggregation>> reportAggregationMap, final Class<?> representationClass, final Object... representationArgs) {
      return reportAggregationMap.values().stream().anyMatch(as -> as != null && as.stream().anyMatch(aggregation ->
         aggregation != null && aggregation.totalStats.getRepresentation(operation, representationClass, representationArgs) != null
      ));
   }

   public void createCharts(String operation, int clusterSize) throws IOException {
      String suffix = clusterSize > 0 ? "_" + clusterSize : "";
      String directory = this.directory.endsWith(File.separator) ? this.directory : this.directory + File.separator;

      List<ChartDescription> charts = generatedCharts.get(operation);
      if (charts == null) {
         generatedCharts.put(operation, charts = new ArrayList<>());
      }
      for (ChartDescription cd : new ChartDescription[] {
         new ChartDescription(ChartType.MEAN_AND_DEV, "mean_dev", "Response time mean", "Response time (ms)"),
         new ChartDescription(ChartType.OPERATION_THROUGHPUT_NET, "throughput_net", "Net operation throughput", "Operations/sec"),
         new ChartDescription(ChartType.DATA_THROUGHPUT, "data_throughput", "Data throughput mean", "MB/sec"),
         new ChartDescription(ChartType.MEAN_AND_DEV_SERIES, "mean_dev_series", "Response time over time", "Response time (ms)"),
         new ChartDescription(ChartType.REQUESTS_SERIES, "requests_series", "Requests progression", "Number of requests"),
         new ChartDescription(ChartType.OPERATION_THROUGHPUT_GROSS_SERIES, "throughput_gross_series", "Operation throughput (gross) over time", "Operations/sec"),
         new ChartDescription(ChartType.OPERATION_THROUGHPUT_NET_SERIES, "throughput_net_series", "Operation throughput (net) over time", "Operations/sec"),
      }) {
         if (createChart(String.format("%s%s%s_%s%s_%s.png", directory, File.separator, testName, operation, suffix, cd.name),
            clusterSize, operation, cd.yLabel, cd.type)) {
            charts.add(cd);
         }
      }
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

   public List<ChartDescription> getGeneratedCharts(String operation) {
      return generatedCharts.getOrDefault(operation, Collections.emptyList());
   }

   public String getHistogramName(Statistics statistics, final String operation, String configurationName, int cluster, int iteration,
                                  String node, Collection<StatisticType> presentedStatistics) {
      String resultFileName = "";
      if (presentedStatistics.contains(StatisticType.HISTOGRAM)) {
         final Histogram histogram = statistics.getRepresentation(operation, Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);
         if (histogram == null) {
            return resultFileName;
         } else {
            resultFileName = String.format("histogram_%s_%s_%s_%d_%d_%s.png", testName, operation, configurationName, cluster, iteration, node);
         }
      }
      return resultFileName;
   }

   public String getPercentileChartName(Statistics statistics, final String operation, String configurationName, int cluster, int iteration,
                                        String node, Collection<StatisticType> presentedStatistics) {
      String resultFileName = "";
      if (presentedStatistics.contains(StatisticType.HISTOGRAM)) {
         final Histogram histogram = statistics.getRepresentation(operation, Histogram.class, configuration.histogramBuckets, configuration.histogramPercentile);
         if (histogram == null) {
            return resultFileName;
         } else {
            resultFileName = String.format("percentiles_%s_%s_%s_%d_%d_%s.png", testName, operation, configurationName, cluster, iteration, node);
         }
      }
      return resultFileName;
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
      if (hasRepresentation(operation, reportAggregationMap, OperationThroughput.class)) {
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
         iterations.add(iterationValues.stream().collect(concatOrDefault("iteration " + String.valueOf(iteration))));
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
      MEAN_AND_DEV(false, "Iteration"),
      OPERATION_THROUGHPUT_NET(false, "Iteration"),
      DATA_THROUGHPUT(false, "Iteration"),
      MEAN_AND_DEV_SERIES(true, "Time (seconds)"),
      REQUESTS_SERIES(true, "Time (seconds)"),
      OPERATION_THROUGHPUT_NET_SERIES(true, "Time (seconds)"),
      OPERATION_THROUGHPUT_GROSS_SERIES(true, "Time (seconds)");

      private final boolean requiresLineChart;
      private final String defaultDomainLabel;

      ChartType(boolean requiresLineChart, String defaultDomainLabel) {
         this.requiresLineChart = requiresLineChart;
         this.defaultDomainLabel = defaultDomainLabel;
      }
   }

   public static class ChartDescription {
      public final ChartType type;
      public final String name;
      public final String title;
      public final String yLabel;

      public ChartDescription(ChartType type, String name, String title, String yLabel) {
         this.type = type;
         this.name = name;
         this.title = title;
         this.yLabel = yLabel;
      }
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
