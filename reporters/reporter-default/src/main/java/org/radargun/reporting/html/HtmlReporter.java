package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HtmlReporter implements Reporter {
   private static final Log log = LogFactory.getLog(HtmlReporter.class);

   @Property(doc = "Directory to put the reports. Default is results/html.")
   private String targetDir = "results" + File.separator + "html";

   @PropertyDelegate(prefix = "timeline.chart.")
   private TimelineDocument.Configuration timelineConfig = new TimelineDocument.Configuration();

   private Scenario scenario;
   private Collection<Report> reports;

   @Override
   public void run(Scenario scenario, Collection<Report> reports) {
      this.scenario = scenario;
      this.reports = reports;
      IndexDocument index = new IndexDocument(targetDir);;
      try {
         index.open();
         index.writeConfigurations(reports);
         index.writeScenario(scenario);
         index.writeTimelines(reports);
         index.writeTests(reports);
         index.writeFooter();
      } catch (IOException e) {
         log.error("Failed to write HTML report.", e);
      } finally {
         index.close();
      }
      Map<String, Integer> clusterIndices = new HashMap<String, Integer>();
      for (Report report : reports) {
         String configName = report.getConfiguration().name;
         Integer clusterIndex = clusterIndices.get(configName);
         if (clusterIndex == null) clusterIndex = 0;
         clusterIndices.put(configName, clusterIndex + 1);

         TimelineDocument timelineDocument = new TimelineDocument(timelineConfig, targetDir,
               configName + "_" + clusterIndex, configName + " on " + report.getCluster(), report.getTimelines());
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
      for (Map.Entry<String, List<Report.Test>> entry : tests.entrySet()) {
         TestReportDocument testReport = new TestReportDocument(targetDir, entry.getKey(), entry.getValue());
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
}
