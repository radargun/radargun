package org.radargun.reporting.perfrepo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXB;

import org.perfrepo.client.PerfRepoClient;
import org.perfrepo.model.TestExecution;
import org.perfrepo.model.TestExecutionParameter;
import org.perfrepo.model.builder.TestExecutionBuilder;
import org.radargun.config.Configuration;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.MainConfig;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.AbstractReporter;
import org.radargun.reporting.Report;
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
public class PerfrepoReporter extends AbstractReporter {

   // See the property value in TestExecutionParameter
   private static final int MAX_VALUE_LENGTH = 2047;

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
   private List<MetricNameMapping> metricNameMapping = new ArrayList<>();
   @Property(doc = "Additional build parameters", converter = KeyValueListConverter.class)
   private Map<String, String> buildParams = new HashMap<>();
   @Property(doc = "File (in java properties format) from which to load additional build parameters")
   private String buildParamsFile = null;
   @Property(doc = "Exclude default build parameters from uploaded entity. Parameters specified in 'buildParams' are not excluded. Default is false.")
   private boolean excludeBuildParams = false;
   @Property(doc = "Exclude service configurations from uploaded entity. Default is false.")
   private boolean excludeNormalizedConfigs = false;
   @Property(doc = "Exclude attachments from uploaded entity. Default is false.")
   private boolean excludeAttachments = false;
   @Property(doc = "Create separate test execution for each test iteration. Default is false.")
   private boolean separateTestIterations = false;

   @Property(doc = "Which configurations should this reporter report. Default is all configurations. Comma separated.")
   private List<String> configurations;
   @Property(doc = "Which tests should this reporter report. Default is all executed tests. Comma separated.")
   private List<String> tests;
   private MainConfig mainConfig;

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
   public void run(MainConfig mainConfig, Collection<Report> reports) throws Exception {
      this.mainConfig = mainConfig;
      if (perfRepoAuth == null) {
         log.error("perfRepoAuth parameter has to be set");
         return;
      }
      if (perfRepoTest == null) {
         log.error("perfRepoTest parameter has to be set");
         return;
      }
      for (Report report : reports) {
         if (configurations == null || configurations.contains(report.getConfiguration().name)) {
            reportTests(report);
         }
      }
   }

   private void reportTests(Report report) throws Exception {
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
         if (separateTestIterations) {
            for (Report.TestIteration iteration : test.getIterations()) {
               TestExecutionBuilder testExecutionBuilder = createTestExecutionBuilder(report, test);

               addIteration(testExecutionBuilder, iteration);
               addIterationParameters(testExecutionBuilder, iteration);
               testExecutionBuilder.tag("iteration_" + iteration.id);

               addConfigsAndUpload(testExecutionBuilder, report, test, perfRepoClient);
            }

         } else {
            TestExecutionBuilder testExecutionBuilder = createTestExecutionBuilder(report, test);

            for (Report.TestIteration iteration : test.getIterations()) {
               addIteration(testExecutionBuilder, iteration);
               addIterationParameters(testExecutionBuilder, iteration);
            }

            addConfigsAndUpload(testExecutionBuilder, report, test, perfRepoClient);
         }
      }
   }

   private void addIteration(TestExecutionBuilder testExecutionBuilder, Report.TestIteration iteration) {
      // merge statistics & aggregate mrds
      Map<MetricNameMapping, List<Double>> mrdMapping = new HashMap<>();
      for (MetricNameMapping mapping : metricNameMapping) {
         if (mapping.computeMRD) {
            mrdMapping.put(mapping, new ArrayList<>());
         }
      }
      iteration.getStatistics().stream().map(workerStats -> workerStats.getValue().stream().reduce(Statistics.MERGE)
         .map(statistics -> addRepresentationValues(mrdMapping, statistics)))
         .filter(Optional::isPresent).map(Optional::get).reduce(Statistics.MERGE).ifPresent(aggregatedStatistics -> {
            long duration = TimeUnit.MILLISECONDS.toNanos(aggregatedStatistics.getEnd() - aggregatedStatistics.getBegin());
            String iterationsName = iteration.test.iterationsName == null ? "Iteration" : iteration.test.iterationsName;
            String iterationValue = iteration.getValue() == null ? String.valueOf(iteration.id) : iteration.getValue();
            for (MetricNameMapping mapping : metricNameMapping) {
               if (mapping.computeMRD) {
                  List<Double> mrds = mrdMapping.get(mapping);
                  if (!mrds.isEmpty()) {
                     testExecutionBuilder.value(mapping.to, StatsUtils.calculateMrd(mrds), iterationsName, iterationValue);
                  }
               } else {
                  double value = mapping.representation.getValue(aggregatedStatistics, mapping.operation, duration);
                  testExecutionBuilder.value(mapping.to, value, iterationsName, iterationValue);
               }
            }
         });
   }

   private Statistics addRepresentationValues(Map<MetricNameMapping, List<Double>> mrdMapping, Statistics statistics) {
      for (Map.Entry<MetricNameMapping, List<Double>> entry : mrdMapping.entrySet()) {
         MetricNameMapping mapping = entry.getKey();
         long duration = TimeUnit.MILLISECONDS.toNanos(statistics.getEnd() - statistics.getBegin());
         double value = mapping.representation.getValue(statistics, mapping.operation, duration);
         entry.getValue().add(value);
      }
      return statistics;
   }

   private void addConfigsAndUpload(TestExecutionBuilder testExecutionBuilder, Report report, Report.Test test, PerfRepoClient perfRepoClient) throws Exception {
      // set normalized configs
      addNormalizedConfigs(report, testExecutionBuilder);
      // create new test execution
      TestExecution testExecution = testExecutionBuilder.build();
      if (testExecution.getParameters() != null) {
         for (TestExecutionParameter parameter : testExecution.getParameters()) {
            String value = parameter.getValue();
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
               parameter.setValue(value.substring(0, MAX_VALUE_LENGTH - 3) + "...");
            }
         }
      }
      try {
         Long executionId = perfRepoClient.createTestExecution(testExecution);
         if (executionId == null) {
            logAndThrowTestExecutionFailure(testExecution);
         }
         // add attachments
         uploadAttachments(report, perfRepoClient, executionId);
      } catch (Exception e) {
         throw new Exception("Error while creating test execution for test " + test.name, e);
      }
   }

   private void logAndThrowTestExecutionFailure(TestExecution testExecution) {
      StringWriter sw = new StringWriter();
      JAXB.marshal(testExecution, sw);
      String xmlString = sw.toString();
      String message = String.format("Cannot create test execution:\n%s\nCheck Tests metrics in PerfRepo.", xmlString);
      throw new IllegalStateException(message);
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
      addBasicParameters(testExecutionBuilder, report);
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

   private void addBasicParameters(TestExecutionBuilder testExecutionBuilder, Report report) {
      testExecutionBuilder.parameter("exec.config", report.getConfiguration().name);
      testExecutionBuilder.parameter("exec.jenkins_build_url", jenkinsBuildUrl);
      testExecutionBuilder.parameter("exec.jenkins_build_number", jenkinsBuild);
      if (buildParams != null) {
         for (Map.Entry<String, String> buildParam : buildParams.entrySet()) {
            testExecutionBuilder.parameter(buildParam.getKey(), buildParam.getValue());
         }
      }
      if (buildParamsFile != null) {
         Properties paramsFromFile = new Properties();
         try (FileInputStream fileInputStream = new FileInputStream(buildParamsFile)) {
            paramsFromFile.load(fileInputStream);
         } catch (IOException e) {
            log.warn("Error while loading build parameters from file", e);
         }
         for (Map.Entry<Object, Object> entry : paramsFromFile.entrySet()) {
            testExecutionBuilder.parameter((String) entry.getKey(), (String) entry.getValue());
         }
      }
   }

   private void addIterationParameters(TestExecutionBuilder testExecutionBuilder, Report.TestIteration iteration) {
      if (excludeBuildParams) {
         return;
      }
      for (Report.TestResult result : iteration.getResults().values()) {
         String resultPrefix = "result." + iteration.id + "." + result.name;
         testExecutionBuilder.parameter(resultPrefix + ".aggregated", result.aggregatedValue);
         for (Map.Entry<Integer, Report.WorkerResult> workerResult : result.workerResults.entrySet()) {
            testExecutionBuilder.parameter(resultPrefix + "." + workerResult.getKey(), workerResult.getValue().value);
         }
      }
   }

   private void addNormalizedConfigs(Report report, TestExecutionBuilder testExecutionBuilder) {
      if (excludeNormalizedConfigs) {
         return;
      }
      for (Map.Entry<Integer, Map<String, Properties>> normalizedConfig : report.getNormalizedServiceConfigs().entrySet()) {
         for (Map.Entry<String, Properties> configItem : normalizedConfig.getValue().entrySet()) {
            if (configItem.getValue() != null) {
               for (Map.Entry<Object, Object> property : configItem.getValue().entrySet()) {
                  StringBuilder key = new StringBuilder();
                  key.append("worker")
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
      if (excludeAttachments) {
         return;
      }
      File file = null;
      try {
         file = File.createTempFile("configs", ".zip");
      } catch (IOException e) {
         log.error("Error while creating a temporary file", e);
      }
      try (FileOutputStream fos = new FileOutputStream(file); ZipOutputStream zos = new ZipOutputStream(fos)) {
         if (executionId == null) {
            log.debug("No execution ID, attachment not uploaded");
            return;
         }
         boolean contentExists = false;
         for (Map.Entry<Integer, Map<String, byte[]>> originalConfig : report.getOriginalServiceConfig().entrySet()) {
            for (Map.Entry<String, byte[]> configItem : originalConfig.getValue().entrySet()) {
               if (configItem.getValue() != null && configItem.getValue().length > 0) {
                  zos.putNextEntry(new ZipEntry("worker" + originalConfig.getKey() + "_" + configItem.getKey()));
                  zos.write(configItem.getValue());
                  zos.closeEntry();
                  contentExists = true;
               }
            }
         }
         if (mainConfig != null) {
            zos.putNextEntry(new ZipEntry("main_config.xml"));
            zos.write(mainConfig.getMainConfigBytes());
            zos.closeEntry();
            contentExists = true;
            if (mainConfig.getScenarioBytes() != null) {
               zos.putNextEntry(new ZipEntry("main_scenario.xml"));
               zos.write(mainConfig.getScenarioBytes());
               zos.closeEntry();
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
         super(new Class[] {MetricNameMapping.class});
      }
   }
}