package org.cachebench.reportgenerators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.config.ClusterConfig;
import org.cachebench.tests.results.TestResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base implementation of {@link org.cachebench.reportgenerators.ReportGenerator}
 */
public abstract class AbstractReportGenerator implements ReportGenerator {
   protected File output;
   protected List<TestResult> results;
   protected ClusterConfig clusterConfig;
   protected Map<String, String> params;
   private static final Log log = LogFactory.getLog(AbstractReportGenerator.class);

   public void setConfigParams(Map<String, String> params) {
      this.params = params;
   }

   public void setOutputFile(String fileName) {
      this.output = new File(getFileName(fileName));
      log.info("Writing output to file " + output);
   }

   private String getFileName(String fileName) {
      String fn = fileName;
      if (fileName.indexOf("-generic-") >= 0) {
         fn = preserveDirectory(fileName) + "data_" + params.get("cacheProductName") + "_" + params.get("config") + "_" +
               (params.containsKey("clusterSize") ? params.get("clusterSize") : "LOCAL") +
               ".csv";
      }
      return fn;
   }

   private String preserveDirectory(String f) {
      if (f.contains(File.separator))
         return f.substring(0, f.lastIndexOf(File.separator)) + File.separator;
      else
         return "";
   }

   public void setResults(List<TestResult> results) {

      this.results = new ArrayList(results);
   }

   public void setClusterConfig(ClusterConfig clusterConfig) {
      this.clusterConfig = clusterConfig;
   }
}
