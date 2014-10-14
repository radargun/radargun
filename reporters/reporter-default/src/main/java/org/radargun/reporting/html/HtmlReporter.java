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

   @PropertyDelegate(prefix = "testReport.")
   private ReportDocument.Configuration testReportConfig = new ReportDocument.Configuration();

   @PropertyDelegate(prefix = "timeline.chart.")
   private TimelineDocument.Configuration timelineConfig = new TimelineDocument.Configuration();

   @Override
   public void run(Collection<Report> reports) {
      Set<String> allTests = new TreeSet<>();
      Set<String> combinedTests = new TreeSet<>();
      for (List<String> combination : testReportConfig.combinedTests) {
         StringBuilder sb = new StringBuilder();
         for (String testName : combination) {
            combinedTests.add(testName);
            if (sb.length() != 0) sb.append('_');
            sb.append(testName);
         }
         allTests.add(sb.toString());
      }

      Map<String, List<Report.Test>> testsByName = new HashMap<String, List<Report.Test>>();
      for (Report report : reports) {
         for(Report.Test t : report.getTests()) {
            List<Report.Test> list = testsByName.get(t.name);
            if (list == null) {
               list = new ArrayList<>();
               testsByName.put(t.name, list);
            }
            list.add(t);
            if (!combinedTests.contains(t.name)) {
               allTests.add(t.name);
            }
         }
      }

      IndexDocument index = new IndexDocument(targetDir);
      try {
         index.open();
         index.writeConfigurations(reports);
         index.writeScenario(reports);
         index.writeTimelines(reports);
         index.writeTests(allTests);
         index.writeFooter();
      } catch (IOException e) {
         log.error("Failed to write HTML report.", e);
      } finally {
         index.close();
      }
      for (Report report : reports) {
         String configName = report.getConfiguration().name;

         TimelineDocument timelineDocument = new TimelineDocument(timelineConfig, targetDir,
               configName + "_" + report.getCluster().getClusterIndex(), configName + " on " + report.getCluster(), report.getTimelines(), report.getCluster());
         try {
            timelineDocument.open();
            timelineDocument.writeTimelines();
         } catch (IOException e) {
            log.error("Failed to create timeline report " + configName, e);
         } finally {
            timelineDocument.close();
         }
      }

      for (Map.Entry<String, List<Report.Test>> entry : testsByName.entrySet()) {
         if (combinedTests.contains(entry.getKey())) {
            // do not write TestReportDocument for combined test
            continue;
         }
         TestAggregations ta = new TestAggregations(entry.getKey(), entry.getValue());
         TestReportDocument testReport = new TestReportDocument(ta, targetDir, testReportConfig);
         try {
            testReport.open();
            testReport.writeTest();
         } catch (IOException e) {
            log.error("Failed to create test report " + entry.getKey(), e);
         } finally {
            testReport.close();
         }
      }
      for (List<String> combined : testReportConfig.combinedTests) {
         List<TestAggregations> testAggregations = new ArrayList<>();
         StringBuilder sb = new StringBuilder();
         for (String testName : combined) {
            if (sb.length() != 0) sb.append('_');
            sb.append(testName);
            List<Report.Test> reportedTests = testsByName.get(testName);
            if (reportedTests == null) {
               log.warn("Test " + testName + " was not found!");
               continue;
            }
            TestAggregations ta = new TestAggregations(testName, reportedTests);
            testAggregations.add(ta);
         }
         CombinedReportDocument testReport = new CombinedReportDocument(testAggregations, sb.toString(), targetDir, testReportConfig);
         try {
            testReport.open();
            testReport.writeTest();
         } catch (IOException e) {
            log.error("Failed to create test report combined", e);
         }
         finally {
            testReport.close();
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
