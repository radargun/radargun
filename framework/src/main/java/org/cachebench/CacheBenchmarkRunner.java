package org.cachebench;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.cluster.ClusterBarrier;
import org.cachebench.config.CacheWarmupConfig;
import org.cachebench.config.ConfigBuilder;
import org.cachebench.config.Configuration;
import org.cachebench.config.Report;
import org.cachebench.config.TestCase;
import org.cachebench.config.TestConfig;
import org.cachebench.reportgenerators.ClusterAwareReportGenerator;
import org.cachebench.reportgenerators.ReportGenerator;
import org.cachebench.tests.CacheTest;
import org.cachebench.tests.ClusteredCacheTest;
import org.cachebench.tests.StatisticTest;
import org.cachebench.tests.results.BaseTestResult;
import org.cachebench.tests.results.StatisticTestResult;
import org.cachebench.tests.results.TestResult;
import org.cachebench.utils.Instantiator;
import org.cachebench.warmup.CacheWarmup;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: CacheBenchmarkRunner.java,v 1.1 2007/05/17 07:37:44 msurtani Exp $
 */
public class CacheBenchmarkRunner {

   private Configuration conf;
   private Log log = LogFactory.getLog(CacheBenchmarkRunner.class);
   private Log errorLogger = LogFactory.getLog("CacheException");

   // information about how we are called:

   String cacheProductName;
   String configuraton;
   int clusterSize;
   Map<String, String> systemParams = new HashMap<String, String>();
   boolean localOnly;

   public static void main(String[] args) {
      String conf = null;
      if (args.length == 1) {
         conf = args[0];
      }
      if (conf != null && conf.toLowerCase().endsWith(".xml")) {
         new CacheBenchmarkRunner(conf);
      } else {
         new CacheBenchmarkRunner();
      }
   }

   /**
    * Initialise some params that may be passed in via JVM params
    */
   private void initJVMParams(String defaultCfgFile) {
      boolean useFlatCache = Boolean.getBoolean("cacheBenchFwk.useFlatCache");
      String overallCfg = System.getProperty("cacheBenchFwk.fwkCfgFile", defaultCfgFile);
      String configuraton = System.getProperty("cacheBenchFwk.cacheConfigFile");
      String cacheProductName = System.getProperty("cacheBenchFwk.cacheProductName");
      String suffix = System.getProperty("cacheBenchFwk.productSuffix");
      if (suffix != null && !suffix.trim().equals("")) cacheProductName += suffix;
      String clusterSize = System.getProperty("clusterSize");
      localOnly = Boolean.getBoolean("localOnly");

      systemParams.put("fwk.config", overallCfg);
      if (configuraton != null) systemParams.put("config", configuraton);
      if (cacheProductName != null) systemParams.put("cacheProductName", cacheProductName);
      if (clusterSize != null) systemParams.put("clusterSize", clusterSize);
      if (localOnly) systemParams.put("localOnly", "TRUE");
      if (useFlatCache) systemParams.put("cacheBenchFwk.useFlatCache", "TRUE");
   }

   public CacheBenchmarkRunner(Configuration conf, String cacheProductName, String configuraton, boolean localOnly, boolean start) throws Exception {
      if (!localOnly)
         throw new UnsupportedOperationException("Not implemented!  This constructor is only for local mode.");
      this.conf = conf;
      this.cacheProductName = cacheProductName;
      if (cacheProductName != null) systemParams.put("cacheProductName", cacheProductName);
      this.configuraton = configuraton;
      if (configuraton != null) systemParams.put("config", configuraton);
      this.localOnly = localOnly;
      if (start) start();
   }

   private CacheBenchmarkRunner() {
      this("cachebench.xml");
   }

   private CacheBenchmarkRunner(String s) {
      initJVMParams(s);
      // first, try and find the configuration on the filesystem.
      s = systemParams.get("fwk.config");
      URL confFile = ConfigBuilder.findConfigFile(s);
      if (confFile == null) {
         log.warn("Unable to locate a configuration file " + s + "; Application terminated");
      } else {
         if (log.isDebugEnabled()) log.debug("Using configuration " + confFile);
         log.debug("Parsing configuration");
         try {
            conf = ConfigBuilder.parseConfiguration(confFile);
            start();
         }
         catch (Throwable e) {
            log.warn("Unable to parse configuration file " + confFile + ". Application terminated", e);
            errorLogger.fatal("Unable to parse configuration file " + confFile, e);
         }
      }
   }

   public void start() throws Exception {
      log.info("Starting Benchmarking....");
      List<TestResult> results = runTests(); // Run the tests from this point.
      if (results != null && results.size() != 0) {
         generateReports(results); // Run the reports...
      } else {
         log.warn("No Results to be reported");
      }
      log.info("Benchmarking Completed.  Hope you enjoyed using this! \n");
   }

   CacheWrapper newCache(TestCase test) throws Exception {
      CacheWrapper cache = getCacheWrapperInstance(test);
      if (cache != null) {
         Map<String, String> params = test.getParams();
         // now add the config file, if any is passed in:
         params.putAll(systemParams);
         log.info("Initialising cache with params " + params);
         cache.init(params);
         cache.setUp();
      }
      return cache;
   }

   /**
    * Executes each test case and returns the result.
    *
    * @return The Array of TestResult objects with the results of the tests.
    */
   private List<TestResult> runTests() throws Exception {
      List<TestResult> results = new ArrayList<TestResult>();
      for (TestCase test : conf.getTestCases()) {
         CacheWrapper cache = null;
         try {
            cache = newCache(test);
            if (cache != null) {
               //now start testing
               cache.setUp();
               warmupCache(test, cache);
               List<TestResult> resultsForCache = runTestsOnCache(cache, test);
               if (!localOnly) barrier("AFTER_TEST_RUN");
               shutdownCache(cache);
               results.addAll(resultsForCache);
            }
         }
         catch (Exception e) {
            try {
               shutdownCache(cache);
            }
            catch (Exception e1) {
               //ignore
            }
            log.warn("Unable to Initialize or Setup the Cache - Not performing any tests", e);
            errorLogger.error("Unable to Initialize or Setup the Cache: " + test.getCacheWrapper(), e);
            errorLogger.error("Skipping this test");
         }
      }
      return results;
   }

   private void barrier(String messageName) throws Exception {
      ClusterBarrier barrier = new ClusterBarrier();
      log.trace("Using following cluster config: " + conf.getClusterConfig());
      barrier.setConfig(conf.getClusterConfig());
      barrier.setAcknowledge(true);
      barrier.barrier(messageName);
      log.info("Barrier for '" + messageName + "' finished");

   }

   private void warmupCache(TestCase test, CacheWrapper cache) throws Exception {
      if (!localOnly) barrier("BEFORE_WARMUP");
      log.info("Warming up..");
      CacheWarmupConfig warmupConfig = test.getCacheWarmupConfig();
      log.trace("Warmup config is: " + warmupConfig);
      CacheWarmup warmup = (CacheWarmup) Instantiator.getInstance().createClass(warmupConfig.getWarmupClass());
      warmup.setConfigParams(warmupConfig.getParams());
      warmup.warmup(cache);
      log.info("Warmup ended!");
      if (!localOnly) barrier("AFTER_WARMUP");
   }

   /**
    * Peforms the necessary external tasks for cache benchmarking. These external tasks are defined in the
    * cachebench.xml and would be executed against the cache under test.
    *
    * @param cache      The CacheWrapper for the cache in test.
    * @param testResult The TestResult of the test to which the tasks are executed.
    */
   private TestResult executeTestTasks(CacheWrapper cache, TestResult testResult) {
      try {
         if (conf.isEmptyCacheBetweenTests()) {
            cache.empty();
         }
         if (conf.isGcBetweenTestsEnabled()) {
            System.gc();
            Thread.sleep(conf.getSleepBetweenTests());
         }
      }
      catch (InterruptedException e) {
         // Nothing doing here...
      }
      catch (Exception e) {
         // The Empty barrier of the cache failed. Add a foot note for the TestResult here.
//         testResult.setFootNote("The Cache Empty barrier failed after test case: " + testResult.getTestName() + " : " + testResult.getTestType());
//         errorLogger.error("The Cache Empty barrier failed after test case : " + testResult.getTestName() + ", " + testResult.getTestType(), e);
      }

      return testResult;
   }

   private List<TestResult> runTestsOnCache(CacheWrapper cache, TestCase testCase) {
      List<TestResult> results = new ArrayList<TestResult>();
      for (TestConfig testConfig : testCase.getTests()) {
         CacheTest testInstance = getCacheTest(testConfig);
         if (testInstance instanceof ClusteredCacheTest && localOnly) {
            log.warn("Skipping replicated tests since this is in local mode!");
            continue;
         }

         if (testInstance != null) {
            TestResult result;
            String testName = testConfig.getName();
            String testCaseName = testCase.getName();
            try {
               if (testInstance instanceof StatisticTest) {
                  // create new DescriptiveStatistics and pass it to the test
                  int repeat = testConfig.getRepeat();
                  if (log.isInfoEnabled()) log.info("Running test " + repeat + " times");
                  StatisticTestResult str = new StatisticTestResult();
                  for (int i = 0; i < repeat; i++) {
                     ((StatisticTest) testInstance).doCumulativeTest(testName, cache, testCaseName, conf.getSampleSize(), conf.getNumThreads(), str);
                     if (conf.isEmptyCacheBetweenTests()) {
                        if (conf.isLocalOnly()) {
                           // destroy and restart the cache
                           shutdownCache(cache);
                           if (i != repeat - 1) cache = newCache(testCase);
                        } else
                           cache.empty();
                     }
                     if (conf.isGcBetweenTestsEnabled()) {
                        System.gc();
                        Thread.sleep(conf.getSleepBetweenTests());
                     }
                  }
                  result = str;
               } else {
                  result = testInstance.doTest(testName, cache, testCaseName, conf.getSampleSize(), conf.getNumThreads());
               }
            }
            catch (Exception e) {
               // The test failed. We should add a test result object with a error message and indicate that it failed.
               result = new BaseTestResult();
               result.setTestName(testCaseName);
               result.setTestTime(new Date());
               result.setTestType(testName);

               result.setTestPassed(false);
               result.setErrorMsg("Failed to Execute - See logs for details : " + e.getMessage());
               log.warn("Test case : " + testCaseName + ", Test : " + testName + " - Failed due to", e);
               errorLogger.error("Test case : " + testCaseName + ", Test : " + testName + " - Failed : " + e.getMessage(), e);
            }
            if (!result.isTestPassed() && testCase.isStopOnFailure()) {
               log.warn("The test '" + testCase + "/" + testName + "' failed, exiting...");
               System.exit(1);
            }
            executeTestTasks(cache, result);
            if (!result.isSkipReport()) {
               results.add(result);
            }
         }
      }
      return results;
   }

   private void generateReports(List<TestResult> results) {
      log.info("Generating Reports...");
      for (Report report : conf.getReports()) {
         ReportGenerator generator;
         try {
            generator = getReportGenerator(report);
            if (generator != null) {
               if (generator instanceof ClusterAwareReportGenerator && localOnly)
                  throw new IllegalArgumentException("Configured to run in local mode only, cannot use a clustered report generator!");
               Map<String, String> params = report.getParams();
               params.putAll(systemParams);
               generator.setConfigParams(params);
               generator.setResults(results);
               generator.setClusterConfig(conf.getClusterConfig());
               generator.setOutputFile(report.getOutputFile());
               generator.generate();
               log.info("Report Generation Completed");
            } else {
               log.info("Report not generated - See logs for reasons!!");
            }
         }
         catch (Exception e) {
            log.warn("Unable to generate Report : " + report.getGenerator() + " - See logs for reasons");
            log.warn("Skipping this report");
            errorLogger.error("Unable to generate Report : " + report.getGenerator(), e);
            errorLogger.error("Skipping this report");
         }
      }
   }

   private CacheWrapper getCacheWrapperInstance(TestCase testCaseClass) {
      CacheWrapper cache = null;
      try {
         cache = (CacheWrapper) Instantiator.getInstance().createClass(testCaseClass.getCacheWrapper());

      }
      catch (Exception e) {
         log.warn("Unable to instantiate CacheWrapper class: " + testCaseClass.getCacheWrapper() + " - Not Running any tests");
         errorLogger.error("Unable to instantiate CacheWrapper class: " + testCaseClass.getCacheWrapper(), e);
         errorLogger.error("Skipping this test");
      }
      return cache;
   }

   private ReportGenerator getReportGenerator(Report reportClass) {
      ReportGenerator report = null;
      try {
         report = (ReportGenerator) Instantiator.getInstance().createClass(reportClass.getGenerator());

      }
      catch (Exception e) {
         log.warn("Unable to instantiate ReportGenerator class: " + reportClass.getGenerator() + " - Not generating the report");
         errorLogger.error("Unable to instantiate ReportGenerator class: " + reportClass.getGenerator(), e);
         errorLogger.error("Skipping this report");
      }
      return report;

   }

   private CacheTest getCacheTest(TestConfig testConfig) {
      CacheTest cacheTestClass = null;
      try {
         cacheTestClass = (CacheTest) Instantiator.getInstance().createClass(testConfig.getTestClass());
         conf.setLocalOnly(localOnly);
         cacheTestClass.setConfiguration(conf, testConfig);
      }
      catch (Exception e) {
         log.warn("Unable to instantiate CacheTest class: " + testConfig.getTestClass() + " - Not Running any tests");
         errorLogger.error("Unable to instantiate CacheTest class: " + testConfig.getTestClass(), e);
         errorLogger.error("Skipping this Test");
      }
      return cacheTestClass;

   }

   private void shutdownCache(CacheWrapper cache) {
      try {
         cache.tearDown();
      }
      catch (Exception e) {
         log.warn("Cache Shutdown - Failed.");
         errorLogger.error("Cache Shutdown failed : ", e);
      }
   }
}