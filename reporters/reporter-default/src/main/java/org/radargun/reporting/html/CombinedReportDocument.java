package org.radargun.reporting.html;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.radargun.reporting.Report;
import org.radargun.reporting.commons.Aggregation;
import org.radargun.reporting.commons.TestAggregations;

/**
 * This reporter is used for combining multiple test results into 1 html document Thats specially if you want to compare
 * different test runs
 *
 * @author Vitalii Chepeliuk &lt;vchepeli@redhat.com&gt;
 * @since 2.0
 */
public class CombinedReportDocument extends ReportDocument {

   private List<TestAggregations> testAggregations;
   private Set<String> operations = new HashSet<>();
   private List<String> combined;
   private Set<Integer> clusterSizes = new TreeSet<>();

   public CombinedReportDocument(List<TestAggregations> testAggregations, String testName, List<String> combined, String targetDir, Configuration configuration) {
      super(targetDir, testName,
            testAggregations.stream().map(ta -> ta.byReports().size()).max(Integer::max).get(),
            testAggregations.stream().map(ta -> ta.getAllClusters().size()).max(Integer::max).get(),
            testAggregations.stream().map(ta -> ta.getMaxIterations()).max(Integer::max).get(),
            configuration);
      this.testAggregations = testAggregations;
      this.combined = combined;
      for (TestAggregations h : testAggregations) {
         operations.addAll(h.getAllOperations());
      }
   }

   @Override
   protected ComparisonChart generateChart(int clusterSize, String operation, String rangeAxisLabel, ChartType chartType) {
      String iterationsName = testAggregations.stream().map(ta -> ta.iterationsName).collect(concatOrDefault(null));
      ComparisonChart chart = createComparisonChart(iterationsName, rangeAxisLabel, chartType);
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
         addToChart(chart, subCategory, operation, chartType, reportAggregationMap);
      }
      return chart;
   }

   public void calculateClusterSizes() {
      for (TestAggregations ta : testAggregations) {
         clusterSizes.addAll(ta.byClusterSize().keySet());
      }
   }

   public void createTestCharts() {
      for (String operation : operations) {
         if (maxClusters > 1 && configuration.separateClusterCharts) {
            for (Integer clusterSize : clusterSizes) {
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
         int i = 0;
         for (TestAggregations ta : testAggregations) {
            createHistogramAndPercentileCharts(operation, ta.byReports(), combined.get(i++));
         }
      }
      waitForChartsGeneration();
   }

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */

   public String getSingleTestName(int i) {
      return combined.get(i);
   }

   public Set<Integer> getClusterSizes() {
      return clusterSizes;
   }

   public List<TestAggregations> getTestAggregations() {
      return testAggregations;
   }

   public Set<String> getOperations() {
      return operations;
   }

   public List<String> getCombined() {
      return combined;
   }

}


