package org.radargun.reporting.html;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.reporting.commons.TestAggregations;


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
      ComparisonChart chart = createComparisonChart(testAggregations.iterationsName, rangeAxisLabel);
      if(!addToChart(chart, subCategory, operation, chartType, reportAggregationMap)){
         chart = null;
      }
      return chart;
   }

   public void writeTest() throws IOException {
      writeTag("h1", "Test " + testName);

      for (Map.Entry<String, Map<Report, List<Report.TestResult>>> result : testAggregations.results().entrySet()) {
         writeTag("h2", result.getKey());
         writeResult(result.getValue());
      }

      for (String operation : testAggregations.getAllOperations()) {
         writeTag("h2", operation);
         if (maxClusters > 1 && configuration.separateClusterCharts) {
            for (Integer clusterSize : testAggregations.byClusterSize().keySet()) {
               createAndWriteCharts(operation, clusterSize);
            }
         } else {
            createAndWriteCharts(operation, 0);
         }
         writeOperation(operation, testAggregations.byReports(), testName);
      }
   }
}
