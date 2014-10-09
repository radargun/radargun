package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.radargun.config.Configuration;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.commons.TestAggregations;

/**
 * Reporter presenting the statistics and timelines in form of directory
 * with several linked HTML pages and image files displayed on those pages.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HtmlReporter implements Reporter {
   private static final Log log = LogFactory.getLog(HtmlReporter.class);

   @Property(doc = "Directory to put the reports. Default is results/html.")
   private String targetDir = "results" + File.separator + "html";

   @Property(doc = "Generate separate charts for different cluster sizes. Default is false.")
   private boolean separateClusterCharts = false;

   @Property(doc = "Generate combined charts for different tests. Default is false.")
   private boolean combineTestReports = false;

   @PropertyDelegate(prefix = "timeline.chart.")
   private TimelineDocument.Configuration timelineConfig = new TimelineDocument.Configuration();

   @Override
   public void run(Collection<Report> reports) {
      IndexDocument index = new IndexDocument(targetDir);
      try {
         index.open();
         index.writeConfigurations(reports);
         index.writeScenario(reports);
         index.writeTimelines(reports);
         index.writeTests(reports, combineTestReports);
         index.writeFooter();
      } catch (IOException e) {
         log.error("Failed to write HTML report.", e);
      } finally {
         index.close();
      }
      for (Report report : reports) {
         String configName = report.getConfiguration().name;

         TimelineDocument timelineDocument = new TimelineDocument(timelineConfig, targetDir,
               configName + "_" + report.getCluster().getClusterIndex(), configName + " on " + report.getCluster(), report.getTimelines());
         try {
            timelineDocument.open();
            timelineDocument.writeTimelines();
         } catch (IOException e) {
            log.error("Failed to create timeline report " + configName, e);
         } finally {
            timelineDocument.close();
         }
      }
      Map<String, List<Report.Test>> tests = new HashMap<String, List<Report.Test>>();
      for (Report report : reports) {
         for(Report.Test t : report.getTests()) {
            List<Report.Test> list = tests.get(t.name);
            if (list == null) {
               list = new ArrayList<Report.Test>();
               tests.put(t.name, list);
            }
            list.add(t);
         }
      }

      if (combineTestReports) {
         List<TestAggregations> testAggregations = new ArrayList<TestAggregations>();
         for (Map.Entry<String, List<Report.Test>> entry : tests.entrySet()) {
            TestAggregations ta = new TestAggregations(entry.getKey(), entry.getValue());
            testAggregations.add(ta);
         }

         CombinedReportDocument testReport = new CombinedReportDocument(testAggregations, targetDir, tests, separateClusterCharts);
         try {
            testReport.open();
            testReport.writeTest();
         } catch (IOException e) {
            log.error("Failed to create test report combined", e);
         }
         finally {
            testReport.close();
         }
      } else {
         for (Map.Entry<String, List<Report.Test>> entry : tests.entrySet()) {
            TestAggregations ta = new TestAggregations(entry.getKey(), entry.getValue());
            TestReportDocument testReport = new TestReportDocument(ta, targetDir, separateClusterCharts);
            try {
               testReport.open();
               testReport.writeTest();
            } catch (IOException e) {
               log.error("Failed to create test report " + entry.getKey(), e);
            } finally {
               testReport.close();
            }
         }
      }

      for (Report report : reports) {
         for (Configuration.Setup setup : report.getConfiguration().getSetups()) {
            Set<Integer> slaves = report.getCluster().getSlaves(setup.group);
            Set<String> normalized = new HashSet<>();
            for (Map.Entry<Integer, Map<String, Properties>> entry : report.getNormalizedServiceConfigs().entrySet()) {
               if (slaves.contains(entry.getKey()) && entry.getValue() != null) {
                  normalized.addAll(entry.getValue().keySet());
               }
            }
            for (String config : normalized) {
               NormalizedConfigDocument document = new NormalizedConfigDocument(
                     targetDir, report.getConfiguration().name, setup.group, report.getCluster(), config, report.getNormalizedServiceConfigs(), slaves);
               try {
                  document.open();
                  document.writeProperties();
               } catch (IOException e) {
                  log.error("Failed to write normalized configuration", e);
               } finally {
                  document.close();
               }
            }
         }
      }
   }
}
