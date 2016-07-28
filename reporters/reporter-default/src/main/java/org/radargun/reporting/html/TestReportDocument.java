package org.radargun.reporting.html;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.reporting.commons.TestAggregations;

/**
 * Shows results of the tests executed in the benchmark.
 * Also creates the image files displayed in this HTML document.
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
      for (String operation : testAggregations.getAllOperations()) {
         if (maxClusters > 1 && configuration.separateClusterCharts) {
            for (Integer clusterSize : testAggregations.byClusterSize().keySet()) {
               try {
                  createCharts(operation, clusterSize);
               } catch (IOException e) {
                  log.error("Exception while creating test charts", e);
               }
            }
         } else {
            try {
               createCharts(operation, 0);
            } catch (IOException e) {
               log.error("Exception while creating test charts", e);
            }
         }
         createHistogramAndPercentileCharts(operation, testAggregations.byReports(), testName);
      }
      waitForChartsGeneration();
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
}
