package org.radargun.reporting.html;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radargun.reporting.Report;
import org.radargun.reporting.commons.TestAggregations;
import org.radargun.utils.Projections;

/**
 * This reporter is used for combining multiple test results into 1 html document Thats specially if you want to compare
 * different test runs
 *
 * @author Vitalii Chepeliuk &lt;vchepeli@redhat.com&gt;
 * @since 2.0
 */
public class CombinedReportDocument extends ReportDocument {

   private List<TestAggregations> testAggregations;
   Set<String> operations = new HashSet<>();

   public CombinedReportDocument(List<TestAggregations> testAggregations, String testName, String targetDir, Configuration configuration) {
      super(targetDir, testName, Projections.max(Projections.project(testAggregations, new Projections.Func<TestAggregations, Integer>() {
         @Override
         public Integer project(TestAggregations testAggregations) {
            return testAggregations.byReports().size();
         }
      })), Projections.max(Projections.project(testAggregations, new Projections.Func<TestAggregations, Integer>() {
         @Override
         public Integer project(TestAggregations testAggregations) {
            return testAggregations.getAllClusters().size();
         }
      })), Projections.max(Projections.project(testAggregations, new Projections.Func<TestAggregations, Integer>() {
         @Override
         public Integer project(TestAggregations testAggregations) {
            return testAggregations.getMaxIterations();
         }
      })), configuration);
      this.testAggregations = testAggregations;
      for (TestAggregations h : testAggregations) {
         operations.addAll(h.getAllOperations());
      }
   }

   @Override
   protected ComparisonChart generateChart(ComparisonChart chart, String operation, String rangeAxisLabel, StatisticType statisticType, DomainAxisContents domainAxisContents) {
      for (TestAggregations ta : testAggregations) {
         chart = createComparisonChart(chart, ta.testName(), operation, rangeAxisLabel, statisticType, ta.byReports(), domainAxisContents);
      }
      return chart;
   }

   public void writeTest() throws IOException {
      writeTag("h1", "Test " + testName);

      for (TestAggregations ta : testAggregations) {
         for (Map.Entry<String, Map<Report, List<Report.TestResult>>> result : ta.results().entrySet()) {
            writeTag("h2", result.getKey());
            writeResult(result.getValue());
         }
      }

      for (String operation : operations) {
         writeTag("h2", operation);
         if (configuration.separateClusterCharts) {
            for (TestAggregations ta : testAggregations) {
               for (Integer clusterSize : ta.byClusterSize().keySet()) {
                  createAndWriteCharts(operation, "_" + clusterSize);
                  writeOperation(operation, ta.byReports());
               }
            }
         } else {
            createAndWriteCharts(operation, "");
            for (TestAggregations ta : testAggregations)
               writeOperation(operation, ta.byReports());
         }
      }
   }
}


