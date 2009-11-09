package org.cachebench


import apache.commons.logging.LogFactory
import java.io.File
import org.cachebench.config._
import org.cachebench.fwk.config.{ConfigFactory, LocalModeConfig}
import org.cachebench.plugins.PluginLocator
import org.cachebench.reportgenerators.CsvStatisticReportGenerator
import warmup.NoCacheWarmup

/**
 * Runs the benchmark in LOCAL mode
 * @author Manik Surtani
 */

object LocalModeRunner {
   val log = LogFactory.getLog(LocalModeRunner.getClass())

   def main(args: Array[String]) {
      // what do we need to do in LOCAL mode?

      // Step1 - grab a hold of the config file to use.
      var cfgFile: String = null

      var nextvalue = false
      for (arg <- args) {
         if (arg equals "-c")
            nextvalue = true
         else if (nextvalue) {
            nextvalue = false
            cfgFile = arg
         }
      }

      log.info("Starting in LOCAL mode, using config file " + cfgFile)
      val configuration = new ConfigFactory().createLocalModeConfig(cfgFile)
      log.info("Parsed cfg as " + configuration)

      // now convert these into "old-world" config beans and pass it to the old framework since this part worked just
      // fine.

      val cbr = createLegacyBenchmarkRunner(configuration)
      cbr.start()
   }

   def createLegacyBenchmarkRunner(cfg: LocalModeConfig): CacheBenchmarkRunner = {

      def nvpairCreator(n: Any, v:Any) = new NVPair(n.toString, v.toString)
      def toPercentStr(f: Float) = (100 * f).intValue.toString


      val legacyWarmupCfg: CacheWarmupConfig = new CacheWarmupConfig()
      if (cfg.isUsingWarmup) {
         legacyWarmupCfg setWarmupClass cfg.getWarmupConfig.getClassName
         legacyWarmupCfg addParam nvpairCreator("operationCount", cfg.getWarmupConfig.getIterations)
      } else {
         legacyWarmupCfg setWarmupClass "org.cachebench.warmup.NoCacheWarmup"
      }

      val legacyTestConfig = new TestConfig()
      legacyTestConfig setTestClass cfg.getTestConfig.getClassName
      legacyTestConfig setRepeat (cfg.getTestConfig.getRepeat.intValue)
      legacyTestConfig setName cfg.getTestConfig.getTestName
      legacyTestConfig setPayloadSizeInBytes cfg.getTestConfig.getPayloadSize.intValue
      legacyTestConfig setMeasureMemFootprint cfg.getReport.isMemFootprintChart.booleanValue
      legacyTestConfig addParam nvpairCreator("writePercentage", toPercentStr(cfg.getTestConfig.getWriteRatio.floatValue))

      val legacyTestCase = new TestCase()
      legacyTestCase setCacheWrapper PluginLocator.locatePlugin
      legacyTestCase setStopOnFailure true
      legacyTestCase setCacheWarmupConfig legacyWarmupCfg
      legacyTestCase addTest legacyTestConfig
      legacyTestCase setName (cfg.getTestConfig.getTestName)

      val legacyReportCfg = new Report()
      legacyReportCfg setOutputFile cfg.getReport.getDir + File.separator + "-generic-"
      legacyReportCfg setGenerator "org.cachebench.reportgenerators.CsvStatisticReportGenerator"
      
      val legacyCfg = new Configuration()
      legacyCfg setEmptyCacheBetweenTests true
      legacyCfg setGcBetweenTestsEnabled (cfg.getTestConfig.isGcBeforeRepeat.booleanValue)
      legacyCfg setSleepBetweenTests (cfg.getTestConfig.getRepeatSleep.intValue)
      legacyCfg setLocalOnly true
      legacyCfg setNumThreads (cfg.getTestConfig.getThreads.intValue)
      legacyCfg setUseTransactions cfg.getTestConfig.isTransactional.booleanValue
      legacyCfg setSampleSize (cfg.getTestConfig.getIterations.intValue)
      legacyCfg addTestCase legacyTestCase
      legacyCfg addReport legacyReportCfg

      return new CacheBenchmarkRunner(legacyCfg, cfg.getBenchmarkName, cfg.getTestConfig.getPluginConfig, true, false)
   }
}