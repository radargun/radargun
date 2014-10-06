package org.radargun.reporting.html;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.radargun.reporting.Report;
import org.radargun.reporting.commons.AggregationHolder;

import static org.radargun.reporting.commons.AggregationHolder.operations;

/**
 * Shows results of the tests executed in the benchmark.
 * Also creates the image files displayed in this HTML document.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
// TODO: reduce max report size in order to not overload browser with huge tables
public class TestReportDocument extends ReportDocument {

   private AggregationHolder holder;

   public TestReportDocument(AggregationHolder holder, String targetDir, boolean separateClusterCharts) {
      super(targetDir, holder.testName(), holder.tests().size(), separateClusterCharts);
      this.holder = holder;
   }

   @Override
   protected ComparisonChart generateChart(ComparisonChart chart, String operation, String rangeAxisLabel, StatisticType statisticType, boolean xLabelClusterSize) {
      return createComparisonChart(chart, "", operation, rangeAxisLabel, statisticType, holder.byReports(), xLabelClusterSize);
   }

   public void writeTest() throws IOException {
      writeTag("h1", "Test " + testName);

      for (Map.Entry<String, Map<Report, List<Report.TestResult>>> result : holder.results().entrySet()) {
         writeTag("h2", result.getKey());
         writeResult(result.getValue());
      }

      for (String operation : operations()) {
         writeTag("h2", operation);
         if (separateClusterCharts) {
               for (Integer clusterSize : holder.byClusterSize().keySet()) {
                  createAndWriteCharts(operation, "_" + clusterSize);
                  writeOperation(operation, holder.byReports());
               }
         } else {
            createAndWriteCharts(operation, "");
               writeOperation(operation, holder.byReports());
         }
      }
   }

}
