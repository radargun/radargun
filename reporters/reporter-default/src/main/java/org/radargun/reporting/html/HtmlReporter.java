package org.radargun.reporting.html;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateModelException;
import org.radargun.config.Configuration;
import org.radargun.config.MasterConfig;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.Timeline;
import org.radargun.reporting.commons.TestAggregations;

/**
 * Reporter presenting the statistics and timelines in form of directory
 * with several linked HTML pages and image files displayed on those pages.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HtmlReporter implements Reporter {
   private static final Log log = LogFactory.getLog(HtmlReporter.class);
   /**
    * Shared executor used for long-running tasks when the report is generated.
    */
   public static final ExecutorService executor = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());

   @Property(doc = "Directory to put the reports. Default is results/html.")
   private String targetDir = "results" + File.separator + "html";

   @PropertyDelegate(prefix = "testReport.")
   private ReportDocument.Configuration testReportConfig = new ReportDocument.Configuration();

   @PropertyDelegate(prefix = "timeline.")
   private TimelineDocument.Configuration timelineConfig = new TimelineDocument.Configuration();

   private Set<String> allTests = new LinkedHashSet<>();
   private Collection<Report> reports;

   @Override
   public void run(MasterConfig masterConfig, Collection<Report> reports) {
      this.reports = reports;
      Set<String> allTests = new LinkedHashSet<>();
      Set<String> combinedTests = new LinkedHashSet<>();
      Map<String, List<Report.Test>> testsByName = new HashMap<>();

      resolveCombinedTests(allTests, combinedTests);
      resolveTestsByName(reports, allTests, combinedTests, testsByName);

      this.allTests = allTests;

      writeIndexDocument(masterConfig, reports);
      writeTimelineDocuments(reports, Timeline.Category.Type.CUSTOM);
      writeTimelineDocuments(reports, Timeline.Category.Type.SYSMONITOR);
      writeTestReportDocuments(combinedTests, testsByName);
      writeCombinedReportDocuments(testsByName);
      writeNormalizedConfigDocuments(reports);

   }

   private void resolveCombinedTests(Set<String> allTests, Set<String> combinedTests) {
      for (List<String> combination : testReportConfig.combinedTests) {
         StringBuilder sb = new StringBuilder();
         for (String testName : combination) {
            combinedTests.add(testName);
            if (sb.length() != 0) sb.append('_');
            sb.append(testName);
         }
         allTests.add(sb.toString());
      }
   }

   private void resolveTestsByName(Collection<Report> reports, Set<String> allTests, Set<String> combinedTests, Map<String, List<Report.Test>> testsByName) {
      for (Report report : reports) {
         for (Report.Test test : report.getTests()) {
            List<Report.Test> list = testsByName.get(test.name);
            if (list == null) {
               list = new ArrayList<>();
               testsByName.put(test.name, list);
            }
            list.add(test);
            if (!combinedTests.contains(test.name)) {
               allTests.add(test.name);
            }
         }
      }
   }

   private void writeNormalizedConfigDocuments(Collection<Report> reports) {
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

               document.createReportDirectory();

               Map root = new HashMap();
               root.put("normalized", document);

               processTemplate(root, targetDir, document.getFileName(), "normalizedReport.ftl");
            }
         }
      }
   }

   private void writeCombinedReportDocuments(Map<String, List<Report.Test>> testsByName) {
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
         if (testAggregations.isEmpty()) {
            log.warn("No tests to combine");
            return;
         }
         CombinedReportDocument testReport = new CombinedReportDocument(testAggregations, sb.toString(), combined, targetDir, testReportConfig);

         testReport.createReportDirectory();
         testReport.calculateClusterSizes();
         testReport.createTestCharts();

         Map root = new HashMap();
         root.put("testReport", testReport);
         root.put("enums", DefaultObjectWrapper.getDefaultInstance().getEnumModels());

         processTemplate(root, targetDir, "test_" + testReport.testName + ".html", "testReport.ftl");

      }
   }

   private void writeTestReportDocuments(Set<String> combinedTests, Map<String, List<Report.Test>> testsByName) {
      for (Map.Entry<String, List<Report.Test>> entry : testsByName.entrySet()) {
         if (combinedTests.contains(entry.getKey())) {
            // do not write TestReportDocument for combined test
            continue;
         }
         TestAggregations ta = new TestAggregations(entry.getKey(), entry.getValue());
         TestReportDocument testReport = new TestReportDocument(ta, targetDir, testReportConfig);

         testReport.createReportDirectory();
         testReport.createTestCharts();

         Map root = new HashMap();
         root.put("testReport", testReport);
         root.put("enums", DefaultObjectWrapper.getDefaultInstance().getEnumModels());

         processTemplate(root, targetDir, "test_" + testReport.testName + ".html", "testReport.ftl");
      }
   }

   private void writeTimelineDocuments(Collection<Report> reports, Timeline.Category.Type categoryType) {
      for (Report report : reports) {
         String configName = report.getConfiguration().name;
         TimelineDocument timelineDocument = new TimelineDocument(timelineConfig, targetDir,
            configName + "_" + report.getCluster().getClusterIndex(), configName + " on " + report.getCluster(), report.getTimelines(), categoryType, report.getCluster());

         timelineDocument.createReportDirectory();
         timelineDocument.createTestCharts();

         Map root = new HashMap();
         root.put("timelineDocument", timelineDocument);
         root.put("categoryType", categoryType.toString());

         exposeStaticMethods(root, "java.lang.String", "String");
         processTemplate(root, targetDir, timelineDocument.getFileName(), "timelineReport.ftl");
      }
   }

   private void writeIndexDocument(MasterConfig masterConfig, Collection<Report> reports) {
      IndexDocument index = new IndexDocument(targetDir);
      index.createReportDirectory();
      index.writeMasterConfig(masterConfig);
      index.prepareServiceConfigs(reports);

      try {
         copyResources("html/templates/", "style.css");
         copyResources("html/templates/", "script.js");
         copyResources("html/icons/", "ic_arrow_drop_down_black_24dp.png");
         copyResources("html/icons/", "ic_arrow_drop_up_black_24dp.png");
      } catch (IOException e) {
         log.error("Failed to copy resources", e);
      }

      Map root = new HashMap();
      root.put("reporter", this);
      root.put("indexDocument", index);

      processTemplate(root, targetDir, "index.html", "index.ftl");
   }

   /**
    * Allows to use static methods in the template
    *
    * @param root        map to which String will be added
    * @param className   full name of class which is to be exposed to the template e.g. java.lang.String
    * @param exposedName name by which the class will be accessed in the template e.g. String
    */
   private void exposeStaticMethods(Map root, String className, String exposedName) {
      DefaultObjectWrapperBuilder builder = new DefaultObjectWrapperBuilder(freemarker.template.Configuration.VERSION_2_3_23);
      try {
         root.put(exposedName, builder.build().getStaticModels().get(className));
      } catch (TemplateModelException e) {
         log.error("Error while getting static class", e);
      }
   }

   /**
    * Creates html file from template
    *
    * @param root         Map of objects that are exposed to the template
    * @param targetDir    target directory for the html file
    * @param fileName     name of html file e.g. index.html
    * @param templateName name of template file e.g. index.ftl
    */
   public static void processTemplate(Map root, String targetDir, String fileName, String templateName) {
      freemarker.template.Configuration cfg = initConfig();
      try (PrintWriter printWriter = new PrintWriter(targetDir + File.separator + fileName)) {
         Template reportTemplate = cfg.getTemplate(templateName);
         reportTemplate.process(root, printWriter);
      } catch (Exception e) {
         log.error("Templating exception", e);
      }
   }

   /**
    * Copies files from resources to targetDir destination
    * This is used to copy css, js  and icon files to target dir
    *
    * @param path to file
    * @param file to be copied
    * @throws IOException if file can not be copied
    */
   private void copyResources(String path, String file) throws IOException {
      ClassLoader classLoader = getClass().getClassLoader();
      try (InputStream source = classLoader.getResourceAsStream(path + file)) {
         File destination = new File(targetDir + File.separator + file);
         Files.copy(source, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
         log.error("Exception while copying resources", e);
      }
   }

   /**
    * Initializes Freemarker configuration
    *
    * @return created configuration
    */
   public static freemarker.template.Configuration initConfig() {
      freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_23);
      cfg.setTemplateLoader(new ClassTemplateLoader(HtmlReporter.class, "/html/templates"));

      DefaultObjectWrapperBuilder builder = new DefaultObjectWrapperBuilder(freemarker.template.Configuration.VERSION_2_3_23);
      // grants access to public fields
      builder.setExposeFields(true);
      DefaultObjectWrapper bw = builder.build();
      // allows to use ?api construct in templates
      cfg.setAPIBuiltinEnabled(true);
      // setting how booleans are displayed, default results in error
      cfg.setBooleanFormat("True, False");
      cfg.setObjectWrapper(bw);
      return cfg;
   }

   /**
    * The following methods are used in Freemarker templates
    * e.g. method getPercentiles() can be used as getPercentiles() or percentiles in template
    */

   public String getSystemProperty(String property) {
      return System.getProperty(property);
   }

   public Set<String> getAllTests() {
      return allTests;
   }

   public Collection<Report> getReports() {
      return reports;
   }

   public boolean hasReportsWithValuesOfType(Timeline.Category.Type type) {
      return reports.stream().anyMatch(r -> r.hasTimelineWithValuesOfType(type));
   }
}
