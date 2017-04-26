package org.radargun.reporting.html;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.radargun.config.Cluster;
import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.reporting.commons.TestAggregations;
import org.radargun.stats.representation.Histogram;

/**
 * Shows results of the tests executed in the benchmark.
 * Also creates the image files displayed in this HTML document.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
// TODO: reduce max report size in order to not overload browser with huge tables
public class TestReportDocument extends ReportDocument {

   private TestAggregations testAggregations;

   public TestReportDocument(TestAggregations testAggregations, String targetDir, Configuration configuration) {
      super(targetDir, testAggregations.testName, testAggregations.byReports().size(), testAggregations.getAllClusters().size(), testAggregations.getMaxIterations(), configuration);
      this.testAggregations = testAggregations;
   }

   @Override
   protected ComparisonChart generateChart(int clusterSize, String operation, String rangeAxisLabel, ChartType chartType) {
      String subCategory;
      Map<Report, List<Aggregation>> reportAggregationMap;
      if (clusterSize > 0) {
         reportAggregationMap = testAggregations.byClusterSize().get(clusterSize);
         subCategory = "size " + clusterSize;
      } else {
         reportAggregationMap = testAggregations.byReports();
         subCategory = null;
      }
      ComparisonChart chart = createComparisonChart(testAggregations.iterationsName, rangeAxisLabel, chartType);
      if (!addToChart(chart, subCategory, operation, chartType, reportAggregationMap)) {
         return null;
      }
      return chart;
   }

   public void createTestCharts() {
      createTestCharts(testAggregations.getOperationGroups());
      createTestCharts(testAggregations.getAllOperations());
      waitForChartsGeneration();
   }

   @Override
   public HistogramChart getHistogramChart(String operation, Cluster cluster, int iteration, int node) {
      HistogramChart chart = new HistogramChart();
      collectHistograms(operation, cluster, iteration, node, chart::addHistogram);
      chart.process(configuration.getHistogramBuckets(), configuration.getHistogramPercentile() / 100d);
      return chart;
   }

   @Override
   public PercentilesChart getPercentilesChart(String operation, Cluster cluster, int iteration, int node) {
      PercentilesChart chart = new PercentilesChart();
      collectHistograms(operation, cluster, iteration, node, chart::addHistogram);
      return chart;
   }

   private void collectHistograms(String operation, Cluster cluster, int iteration, int node, BiConsumer<String, Histogram> collector) {
      Stream<Aggregation> aggregations = testAggregations.byReports().values().stream().flatMap(List::stream);
      collectHistograms(aggregations, operation, cluster, iteration, node, collector);
   }

   private void createTestCharts(Set<String> targets) {
      for (String target : targets) {
         if (maxClusters > 1 && configuration.separateClusterCharts) {
            for (Integer clusterSize : testAggregations.byClusterSize().keySet()) {
               try {
                  createCharts(target, clusterSize);
               } catch (IOException e) {
                  log.error("Exception while creating test charts", e);
               }
            }
         } else {
            try {
               createCharts(target, 0);
            } catch (IOException e) {
               log.error("Exception while creating test charts", e);
            }
         }
      }
   }

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */

   // This returns list so that it is compatible with Combined report document
   public List<TestAggregations> getTestAggregations() {
      return Collections.singletonList(testAggregations);
   }

   public String getSingleTestName(int i) {
      return testName;
   }

   public Set<Integer> getClusterSizes() {
      return testAggregations.byClusterSize().keySet();
   }

   public Set<String> getOperations() {
      return testAggregations.getAllOperations();
   }
   public Set<String> getOperationGroups() {
      return testAggregations.getOperationGroups();
   }
}
