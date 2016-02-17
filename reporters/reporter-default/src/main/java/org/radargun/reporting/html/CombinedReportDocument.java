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
   private Set<String> operations = new TreeSet<>();
   private Set<String> operationGroups = new TreeSet<>();
   private List<String> combined;
   private Set<Integer> clusterSizes = new TreeSet<>();

   public CombinedReportDocument(List<TestAggregations> testAggregations, String testName, List<String> combined, String targetDir, Configuration configuration) {
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
      this.combined = combined;
      for (TestAggregations h : testAggregations) {
         operations.addAll(h.getAllOperations());
         operationGroups.addAll(h.getOperationGroups());
      }
   }

   @Override
   protected ComparisonChart generateChart(int clusterSize, String target, String rangeAxisLabel, ChartType chartType) {
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
         if (!addToChart(chart, subCategory, target, chartType, reportAggregationMap)) {
            chart = null;
         }
      }
      return chart;
   }

   public void calculateClusterSizes() {
      for (TestAggregations ta : testAggregations) {
         clusterSizes.addAll(ta.byClusterSize().keySet());
      }
   }

   public void createTestCharts() {
      createTestCharts(operationGroups);
      createTestCharts(operations);
      waitForChartsGeneration();
   }

   private void createTestCharts(Set<String> targets) {
      for (String target : targets) {
         if (maxClusters > 1 && configuration.separateClusterCharts) {
            for (Integer clusterSize : clusterSizes) {
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
         int i = 0;
         for (TestAggregations ta : testAggregations) {
            createHistogramAndPercentileCharts(target, ta.byReports(), combined.get(i++));
         }
      }
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

   public Set<String> getOperationGroups() {
      return operationGroups;
   }

   public List<String> getCombined() {
      return combined;
   }

}


