package org.radargun.reporting.html;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
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
   protected ComparisonChart generateChart(int clusterSize, String operation, String rangeAxisLabel, StatisticType statisticType) {
      String iterationsName = concatOrDefault(Projections.project(testAggregations, new Projections.Func<TestAggregations, String>() {
         @Override
         public String project(TestAggregations testAggregations) {
            return testAggregations.iterationsName;
         }
      }), null);
      ComparisonChart chart = createComparisonChart(iterationsName, rangeAxisLabel);
      for (TestAggregations ta : testAggregations) {
         String subCategory;
         Map<Report, List<Aggregation>> reportAggregationMap;
         if (clusterSize > 0) {
            reportAggregationMap = ta.byClusterSize().get(clusterSize);
            subCategory = ta.testName + ", size " + clusterSize;
         } else {
            reportAggregationMap = ta.byReports();
            subCategory = ta.testName;
         }
         addToChart(chart, subCategory, operation, statisticType, reportAggregationMap);
      }
      return chart;
   }

   public void writeTest() throws IOException {
      writeTag("h1", "Test " + testName);

      Set<Integer> clusterSizes = new TreeSet<>();
      for (TestAggregations ta : testAggregations) {
         for (Map.Entry<String, Map<Report, List<Report.TestResult>>> result : ta.results().entrySet()) {
            writeTag("h2", result.getKey());
            writeResult(result.getValue());
         }
         clusterSizes.addAll(ta.byClusterSize().keySet());
      }

      for (final String operation : operations) {
         writeTag("h2", operation);
         Map<Report, List<Aggregation>> allAggregations = new TreeMap<>();
         for (TestAggregations ta : testAggregations) {
            allAggregations.putAll(ta.byReports());
         }
         if (configuration.separateClusterCharts) {
            for (Integer clusterSize : clusterSizes) {
               createAndWriteCharts(operation, clusterSize);
            }
         } else {
            createAndWriteCharts(operation, 0);
         }
         writeOperation(operation, allAggregations);
      }
   }
}


