package org.radargun.reporting.perfrepo;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.perfrepo.client.PerfRepoClient;
import org.perfrepo.model.TestExecution;
import org.perfrepo.model.builder.TestExecutionBuilder;
import org.radargun.config.Configuration;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.StatsUtils;
import org.radargun.stats.representation.RepresentationType;
import org.radargun.utils.DateConverter;
import org.radargun.utils.KeyValueListConverter;
import org.radargun.utils.ReflexiveConverters;
import org.radargun.utils.Utils;

/**
 * <p>Reporter providing direct upload of benchmarking results to performance repository using
 * {@link org.perfrepo.client.PerfRepoClient}.</p>
 * <p>Currently supported statistics:
 * <ul>
 * <li>DefaultOutcome - requests, errors, responseTimeMax, responseTimeMean, throughput.theoretical, throughput.actual</li>
 * <li>MeanAndDev - mean, dev</li>
 * <li>Maximum relative difference - enabled by appending '.MRD' to statistics name (e.g. BasicOperations.Get.TX.TheoreticalThroughput.MRD)</li>
 * </ul></p>
 *
 * @author Matej Cimbora
 */
public class PerfrepoReporter implements Reporter {

   private static final Log log = LogFactory.getLog(PerfrepoReporter.class);

   @Property(doc = "Perfrepo host (default localhost)")
   private String perfRepoHost = "localhost";
   @Property(doc = "Perfrepo port (default 8080)")
   private int perfRepoPort = 8080;
   @Property(doc = "Perfrepo BASIC Authentication string (required)")
   private String perfRepoAuth = null;
   @Property(doc = "Perfrepo tags, semicolon separated")
   private String perfRepoTag = null;
   @Property(doc = "Perfrepo test uid (required)")
   private String perfRepoTest = null;
   @Property(doc = "ID of jenkins build that produced this report (default not <unknown>)")
   private String jenkinsBuild = "<unknown>";
   @Property(doc = "URL of jenkins build that produced this report (default <unknown>)")
   private String jenkinsBuildUrl = "<unknown>";
   @Property(doc = "Date of jenkins build that produced this report (default current date)", converter = DateConverter.class)
   private Date jenkinsBuildDate = Calendar.getInstance().getTime();
   @Property(doc = "Name mapping between radargun statistics names and perfrepo metric names. Only statistics with defined mapping will be uploaded.",
         complexConverter = MetricNameMappingConverter.class)
   private List<MetricNameMapping> metricNameMapping = new ArrayList<MetricNameMapping>();
   @Property(doc = "Additional build parameters", converter = KeyValueListConverter.class)
   private Map<String, String> buildParams = new HashMap<>();

   @Property(doc = "Which tests should this reporter report. Default is all executed tests.")
   private List<String> tests;

   @Init
   public void validate() {
      Set<String> mappingTargets = new HashSet<>();
      for (MetricNameMapping mapping : metricNameMapping) {
         if (!mappingTargets.add(mapping.to)) {
            throw new IllegalArgumentException("Multiple mappings to " + mapping.to);
         }
      }
   }

   @Override
   public void run(Collection<Report> reports) {
      if (perfRepoAuth == null) {
         log.error("perfRepoAuth parameter has to be set");
         return;
      }
      if (perfRepoTest == null) {
         log.error("perfRepoTest parameter has to be set");
         return;
      }
      for (Report report : reports) {
         reportTests(report);
      }
   }

   private void reportTests(Report report) {
      String perfRepoAddress = perfRepoHost + ":" + perfRepoPort;
      PerfRepoClient perfRepoClient = new PerfRepoClient(perfRepoAddress, "", perfRepoAuth);

      if (metricNameMapping == null) {
         log.warn("Metric name mapping was not defined!");
         return;
      }
      for (Report.Test test : report.getTests()) {
         if (tests != null && !tests.contains(test.name)) {
            log.debugf("Test %s is not reported.", test.name);
            continue;
         } else {
            log.debugf("Reporting test %s", test.name);
         }
         TestExecutionBuilder testExecutionBuilder = createTestExecutionBuilder(report, test);

         for (Report.TestIteration iteration : test.getIterations()) {
            addIteration(testExecutionBuilder, iteration);
         }

         // set normalized configs
         addNormalizedConfigs(report, testExecutionBuilder);
         // create new test execution
         TestExecution testExecution = testExecutionBuilder.build();
         try {
            Long executionId = perfRepoClient.createTestExecution(testExecution);
            if (executionId != null) {
               // add attachments
               uploadAttachments(report, perfRepoClient, executionId);
            }
         } catch (Exception e) {
            log.error("Error while creating test execution for test " + test.name, e);
         }
      }
   }

   private void addIteration(TestExecutionBuilder testExecutionBuilder, Report.TestIteration iteration) {
      // merge statistics & aggregate mrds
      Statistics aggregatedStatistics = null;
      Map<MetricNameMapping, List<Double>> mrdMapping = new HashMap<>();
      for (MetricNameMapping mapping : metricNameMapping) {
         if (mapping.computeMRD) {
            mrdMapping.put(mapping, new ArrayList<Double>());
         }
      }
      for (Map.Entry<Integer, List<Statistics>> slaveStats : iteration.getStatistics()) {
         Statistics statistics = mergeStatistics(slaveStats.getValue());
         if (aggregatedStatistics == null) {
            aggregatedStatistics = statistics.copy();
         } else {
            aggregatedStatistics.merge(statistics);
         }
         for (Map.Entry<MetricNameMapping, List<Double>> entry : mrdMapping.entrySet()) {
            MetricNameMapping mapping = entry.getKey();
            long duration = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
            OperationStats operationStats = statistics.getOperationsStats().get(mapping.operation);
            double value = mapping.representation.getValue(operationStats, duration);
            entry.getValue().add(value);
         }
      }
      if (aggregatedStatistics != null) {
         long duration = TimeUnit.MILLISECONDS.toNanos(aggregatedStatistics.getEnd() - aggregatedStatistics.getBegin());
         String iterationsName = iteration.test.iterationsName == null ? "Iteration" : iteration.test.iterationsName;
         String iterationValue = iteration.getValue() == null ? String.valueOf(iteration.id) : iteration.getValue();
         for (MetricNameMapping mapping : metricNameMapping) {
            OperationStats operationStats = aggregatedStatistics.getOperationsStats().get(mapping.operation);
            if (operationStats == null) {
               log.warn("No operation " + mapping.operation + " reported!");
               continue;
            }
            if (mapping.computeMRD) {
               List<Double> mrds = mrdMapping.get(mapping);
               if (!mrds.isEmpty()) {
                  testExecutionBuilder.value(mapping.to, StatsUtils.calculateMrd(mrds),  iterationsName, iterationValue);
               }
            } else {
               double value = mapping.representation.getValue(operationStats, duration);
               testExecutionBuilder.value(mapping.to, value, iterationsName, iterationValue);
            }
         }
      }
   }

   private TestExecutionBuilder createTestExecutionBuilder(Report report, Report.Test test) {
      String configName = "JDG RG (" + report.getConfiguration().name + ") " + jenkinsBuild;

      TestExecutionBuilder testExecutionBuilder =
            TestExecution.builder()
                  .name(configName)
                  .testUid(perfRepoTest)
                  .started(jenkinsBuildDate);
      addTags(testExecutionBuilder, report);
      testExecutionBuilder.tag(test.name);
      testExecutionBuilder.parameter("exec.config", report.getConfiguration().name);
      testExecutionBuilder.parameter("exec.jenkins_build_url", jenkinsBuildUrl);
      testExecutionBuilder.parameter("exec.jenkins_build_number", jenkinsBuild);
      for (Report.TestIteration iteration : test.getIterations()) {
         for (Report.TestResult result : iteration.getResults().values()) {
            String resultPrefix = "result." + iteration.id + "." + result.name;
            testExecutionBuilder.parameter(resultPrefix + ".aggregated", result.aggregatedValue);
            for (Map.Entry<Integer, Report.SlaveResult> slaveResult : result.slaveResults.entrySet()) {
               testExecutionBuilder.parameter(resultPrefix + "." + slaveResult.getKey(), slaveResult.getValue().value);
            }
         }
      }
      if (buildParams != null) {
         for (Map.Entry<String, String> buildParam : buildParams.entrySet()) {
            testExecutionBuilder.parameter(buildParam.getKey(), buildParam.getValue());
         }
      }
      return testExecutionBuilder;
   }

   private void addTags(TestExecutionBuilder testExecutionBuilder, Report report) {
      if (perfRepoTag != null) {
         String[] tags = perfRepoTag.split(";");
         for (String tag : tags) {
            testExecutionBuilder.tag(tag);
         }
      }
      for (Configuration.Setup setup : report.getConfiguration().getSetups()) {
         testExecutionBuilder.tag(setup.plugin + "." + setup.service);
      }
      testExecutionBuilder.tag(report.getConfiguration().name);
      testExecutionBuilder.tag("size" + report.getCluster().getSize());
   }

   private Statistics mergeStatistics(List<Statistics> statisticsList) {
      Statistics summary = null;
      for (Statistics statistics : statisticsList) {
         if (summary == null) {
            summary = statistics.copy();
         } else {
            summary.merge(statistics);
         }
      }
      return summary;
   }

   private void addNormalizedConfigs(Report report, TestExecutionBuilder testExecutionBuilder) {
      for (Map.Entry<Integer, Map<String, Properties>> normalizedConfig : report.getNormalizedServiceConfigs().entrySet()) {
         for (Map.Entry<String, Properties> configItem : normalizedConfig.getValue().entrySet()) {
            if (configItem.getValue() != null) {
               for (Map.Entry<Object, Object> property : configItem.getValue().entrySet()) {
                  StringBuilder key = new StringBuilder();
                  key.append("slave")
                        .append(normalizedConfig.getKey())
                        .append(".");
                  key.append(configItem.getKey())
                        .append(".")
                        .append(property.getKey());
                  testExecutionBuilder.parameter(key.toString(), property.getValue() == null ? "null" : (String) property.getValue());
               }
            }
         }
      }
   }

   private void uploadAttachments(Report report, PerfRepoClient perfRepoClient, Long executionId) {
      File file = null;
      try (FileOutputStream fos = new FileOutputStream(file); ZipOutputStream zos = new ZipOutputStream(fos)) {
         file = File.createTempFile("configs", ".zip");
         if (executionId == null) {
            log.debug("No execution ID, attachment not uploaded");
            return;
         }
         boolean contentExists = false;
         for (Map.Entry<Integer, Map<String, byte[]>> originalConfig : report.getOriginalServiceConfig().entrySet()) {
            for (Map.Entry<String, byte[]> configItem : originalConfig.getValue().entrySet()) {
               if (configItem.getValue() != null && configItem.getValue().length > 0) {
                  zos.putNextEntry(new ZipEntry("slave" + originalConfig.getKey() + "_" + configItem.getKey()));
                  zos.write(configItem.getValue());
                  zos.closeEntry();
                  contentExists = true;
               }
            }
         }
         zos.finish();
         Utils.close(zos, fos);
         // avoid uploading empty attachments
         if (contentExists) {
            perfRepoClient.uploadAttachment(executionId, file, "application/zip", "configs.zip");
         }
      } catch (Exception e) {
         log.error("Error while uploading attachment", e);
      } finally {
         if (file != null) {
            if (!file.delete()) {
               file.deleteOnExit();
            }
         }
      }
   }

   @Override
   public String toString() {
      return "PerfrepoReporter" + PropertyHelper.toString(this);
   }

   @DefinitionElement(name = "map", doc = "Definition of mapping to PerfRepo metric.")
   private static class MetricNameMapping {
      @Property(doc = "Operation that should be mapped.", optional = false)
      protected String operation;

      @Property(doc = "Which representation should be retrieved", optional = false,
            converter = RepresentationType.SimpleConverter.class)
      protected RepresentationType representation;

      @Property(doc = "Name of the target metric.", optional = false)
      protected String to;

      @Property(doc = "Compute mean relative deviation of the representation values.")
      protected boolean computeMRD = false;
   }

   private static class MetricNameMappingConverter extends ReflexiveConverters.ListConverter {
      public MetricNameMappingConverter() {
         super(new Class[] { MetricNameMapping.class });
      }
   }
}