package org.radargun.reporting.html;

import org.radargun.reporting.Report;
import org.radargun.reporting.commons.AggregationHolder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.radargun.reporting.commons.AggregationHolder.operations;

/**
 * This reporter is used for combining multiple test results into 1 html document Thats specially if you want to compare
 * different test runs
 *
 * @author Vitalii Chepeliuk &lt;vchepeli@redhat.com&gt;
 * @since 2.0
 */
public class CombinedReportDocument extends ReportDocument {

   private static final String TEST_NAME = "combined";
   private List<AggregationHolder> holders;
   Map<String, List<Report.Test>> tests;

   public CombinedReportDocument(List<AggregationHolder> holders, String targetDir, Map<String, List<Report.Test>> tests, boolean separateClusterCharts) {
      super(targetDir, TEST_NAME, maxSize(tests), separateClusterCharts);
      this.tests = tests;
      this.holders = holders;
   }

   private static int maxSize(Map<String, List<Report.Test>> tests) {
      int maxSize = 0;
      for (Map.Entry<String, List<Report.Test>> entry : tests.entrySet()) {
         maxSize = Math.max(maxSize, entry.getValue().size());
      }
      return maxSize;
   }

   @Override
   protected ComparisonChart generateChart(ComparisonChart chart, String operation, String rangeAxisLabel, StatisticType statisticType, boolean xLabelClusterSize) {
      for (AggregationHolder holder : holders) {
         chart = createComparisonChart(chart, holder.testName(), operation, rangeAxisLabel, statisticType, holder.byReports(),  xLabelClusterSize);
      }
      return chart;
   }

   public void writeTest() throws IOException {
      writeTag("h1", "Test " + TEST_NAME);

      for (AggregationHolder holder : holders) {
         for (Map.Entry<String, Map<Report, List<Report.TestResult>>> result : holder.results().entrySet()) {
            writeTag("h2", result.getKey());
            writeResult(result.getValue());
         }
      }

      for (String operation : operations()) {
         writeTag("h2", operation);
         if (separateClusterCharts) {
            for (AggregationHolder holder : holders) {
               for (Integer clusterSize : holder.byClusterSize().keySet()) {
                  createAndWriteCharts(operation, "_" + clusterSize);
                  writeOperation(operation, holder.byReports());
               }
            }
         } else {
            createAndWriteCharts(operation, "");
            for (AggregationHolder holder : holders)
               writeOperation(operation, holder.byReports());
         }
      }
   }
}


