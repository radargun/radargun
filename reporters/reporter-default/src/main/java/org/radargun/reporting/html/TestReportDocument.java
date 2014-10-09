package org.radargun.reporting.html;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.radargun.reporting.Report;
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

   public TestReportDocument(TestAggregations testAggregations, String targetDir, boolean separateClusterCharts) {
      super(targetDir, testAggregations.testName(), testAggregations.byReports().size(), testAggregations.getAllClusters().size(), testAggregations.getMaxIterations(), separateClusterCharts);
      this.testAggregations = testAggregations;
   }

   @Override
   protected ComparisonChart generateChart(ComparisonChart chart, String operation, String rangeAxisLabel, StatisticType statisticType, boolean xLabelClusterSize) {
      return createComparisonChart(chart, "", operation, rangeAxisLabel, statisticType, testAggregations.byReports(), xLabelClusterSize);
   }

   public void writeTest() throws IOException {
      writeTag("h1", "Test " + testName);

      for (Map.Entry<String, Map<Report, List<Report.TestResult>>> result : testAggregations.results().entrySet()) {
         writeTag("h2", result.getKey());
         writeResult(result.getValue());
      }

      for (String operation : testAggregations.getAllOperations()) {
         writeTag("h2", operation);
         if (separateClusterCharts) {
               for (Integer clusterSize : testAggregations.byClusterSize().keySet()) {
                  createAndWriteCharts(operation, "_" + clusterSize);
                  writeOperation(operation, testAggregations.byReports());
               }
         } else {
            createAndWriteCharts(operation, "");
               writeOperation(operation, testAggregations.byReports());
         }
      }
   }

}
