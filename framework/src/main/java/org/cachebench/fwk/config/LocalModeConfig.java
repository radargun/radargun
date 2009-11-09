package org.cachebench.fwk.config;

import org.cachebench.tests.simpletests.StringTest;
import org.cachebench.warmup.PutGetCacheWarmup;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * A configuration bean to run the benchmark framework in "local" mode.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "local-bench-config")
public class LocalModeConfig {
   @XmlAttribute(required = true, name = "benchmark-name")
   @XmlJavaTypeAdapter(SystemPropertyAwareStringUnmarshaller.class)
   String benchmarkName;

   @XmlElement(name = "warmup")
   WarmupConfig warmupConfig = new WarmupConfig();

   @XmlElement
   ReportConfig report = new ReportConfig();

   @XmlElement(required = true, name = "test")
   TestConfig testConfig = new TestConfig();

   public String getBenchmarkName() {
      return benchmarkName;
   }

   public void setBenchmarkName(String benchmarkName) {
      this.benchmarkName = benchmarkName;
   }

   public WarmupConfig getWarmupConfig() {
      return warmupConfig;
   }

   public void setWarmupConfig(WarmupConfig warmupConfig) {
      this.warmupConfig = warmupConfig;
   }

   public TestConfig getTestConfig() {
      return testConfig;
   }

   public void setTestConfig(TestConfig testConfig) {
      this.testConfig = testConfig;
   }

   public ReportConfig getReport() {
      return report;
   }

   public void setReport(ReportConfig report) {
      this.report = report;
   }

   public boolean isUsingWarmup() {
      return warmupConfig != null && warmupConfig.isEnabled();
   }

   @Override
   public String toString() {
      return "LocalModeConfig{" +
            "benchmarkName='" + benchmarkName + '\'' +
            ", warmupConfig=" + warmupConfig +
            ", report=" + report +
            ", testConfig=" + testConfig +
            '}';
   }

   @XmlAccessorType(XmlAccessType.FIELD)
   @XmlRootElement(name = "report")
   public static class ReportConfig {
      @XmlAttribute
      @XmlJavaTypeAdapter(SystemPropertyAwareStringUnmarshaller.class)
      String dir = "out";

      @XmlAttribute (name="mem-footprint-chart")
      @XmlJavaTypeAdapter(SystemPropertyAwareBooleanUnmarshaller.class)
      Boolean memFootprintChart = false;

      public String getDir() {
         return dir;
      }

      public void setDir(String dir) {
         this.dir = dir;
      }

      public Boolean isMemFootprintChart() {
         return memFootprintChart;
      }

      public void setMemFootprintChart(Boolean memFootprintChart) {
         this.memFootprintChart = memFootprintChart;
      }

      @Override
      public String toString() {
         return "ReportConfig{" +
               "dir='" + dir + '\'' +
               ", memFootprintChart=" + memFootprintChart +
               '}';
      }
   }

   @XmlAccessorType(XmlAccessType.FIELD)
   @XmlRootElement(name = "warmup")
   public static class WarmupConfig {
      @XmlAttribute
      @XmlJavaTypeAdapter(SystemPropertyAwareBooleanUnmarshaller.class)
      Boolean enabled = false;

      @XmlAttribute
      @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
      Integer iterations;

      @XmlAttribute(name = "class")
      @XmlJavaTypeAdapter(SystemPropertyAwareStringUnmarshaller.class)
      String className = PutGetCacheWarmup.class.getName();

      public Boolean isEnabled() {
         return enabled;
      }

      public void setEnabled(Boolean enabled) {
         this.enabled = enabled;
      }

      public Integer getIterations() {
         return iterations;
      }

      public void setIterations(Integer iterations) {
         this.iterations = iterations;
      }

      public String getClassName() {
         return className;
      }

      public void setClassName(String className) {
         this.className = className;
      }

      @Override
      public String toString() {
         return "WarmupConfig{" +
               "enabled=" + enabled +
               ", iterations=" + iterations +
               ", className='" + className + '\'' +
               '}';
      }
   }

   @XmlAccessorType(XmlAccessType.FIELD)
   @XmlRootElement(name = "test")
   public static class TestConfig {
      @XmlAttribute(required = true, name = "plugin-config")
      @XmlJavaTypeAdapter(SystemPropertyAwareStringUnmarshaller.class)
      String pluginConfig;

      @XmlAttribute(required = true)
      @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
      Integer threads;

      @XmlAttribute(required = true)
      @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
      Integer iterations;

      @XmlAttribute(required = true, name = "write-ratio")
      @XmlJavaTypeAdapter(SystemPropertyAwareFloatUnmarshaller.class)
      Float writeRatio;

      @XmlAttribute(name = "class")
      @XmlJavaTypeAdapter(SystemPropertyAwareStringUnmarshaller.class)
      String className = StringTest.class.getName();

      @XmlAttribute(required = true, name = "name")
      @XmlJavaTypeAdapter(SystemPropertyAwareStringUnmarshaller.class)
      String testName;

      @XmlAttribute
      @XmlJavaTypeAdapter(SystemPropertyAwareBooleanUnmarshaller.class)
      Boolean transactional = false;

      @XmlAttribute(name = "gc-before-repeat")
      @XmlJavaTypeAdapter(SystemPropertyAwareBooleanUnmarshaller.class)
      Boolean gcBeforeRepeat = true;

      @XmlAttribute
      @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
      Integer repeat = 1;

      @XmlAttribute(name = "repeat-sleep")
      @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
      Integer repeatSleep = 1000;

      @XmlAttribute(name = "payload-size")
      @XmlJavaTypeAdapter(SystemPropertyAwareIntegerUnmarshaller.class)
      Integer payloadSize = 32;

      public String getPluginConfig() {
         return pluginConfig;
      }

      public void setPluginConfig(String pluginConfig) {
         this.pluginConfig = pluginConfig;
      }

      public Integer getThreads() {
         return threads;
      }

      public void setThreads(Integer threads) {
         this.threads = threads;
      }

      public Integer getIterations() {
         return iterations;
      }

      public void setIterations(Integer iterations) {
         this.iterations = iterations;
      }

      public Float getWriteRatio() {
         return writeRatio;
      }

      public void setWriteRatio(Float writeRatio) {
         this.writeRatio = writeRatio;
      }

      public String getClassName() {
         return className;
      }

      public void setClassName(String className) {
         this.className = className;
      }

      public String getTestName() {
         return testName;
      }

      public void setTestName(String testName) {
         this.testName = testName;
      }

      public Boolean isTransactional() {
         return transactional;
      }

      public void setTransactional(Boolean transactional) {
         this.transactional = transactional;
      }

      public Boolean isGcBeforeRepeat() {
         return gcBeforeRepeat;
      }

      public void setGcBeforeRepeat(Boolean gcBeforeRepeat) {
         this.gcBeforeRepeat = gcBeforeRepeat;
      }

      public Integer getRepeat() {
         return repeat;
      }

      public void setRepeat(Integer repeat) {
         this.repeat = repeat;
      }

      public Integer getRepeatSleep() {
         return repeatSleep;
      }

      public void setRepeatSleep(Integer repeatSleep) {
         this.repeatSleep = repeatSleep;
      }

      public Integer getPayloadSize() {
         return payloadSize;
      }

      public void setPayloadSize(Integer payloadSize) {
         this.payloadSize = payloadSize;
      }

      @Override
      public String toString() {
         return "TestConfig{" +
               "pluginConfig='" + pluginConfig + '\'' +
               ", threads=" + threads +
               ", iterations=" + iterations +
               ", writeRatio=" + writeRatio +
               ", className='" + className + '\'' +
               ", testName='" + testName + '\'' +
               ", transactional=" + transactional +
               ", gcBeforeRepeat=" + gcBeforeRepeat +
               ", repeat=" + repeat +
               ", repeatSleep=" + repeatSleep +
               ", payloadSize=" + payloadSize +
               '}';
      }
   }
}
