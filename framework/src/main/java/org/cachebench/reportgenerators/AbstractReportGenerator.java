package org.cachebench.reportgenerators;

import org.apache.commons.logging.Log;
import org.cachebench.config.ClusterConfig;
import org.cachebench.tests.results.TestResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base implementation of {@link org.cachebench.reportgenerators.ReportGenerator}
 */
public abstract class AbstractReportGenerator implements ReportGenerator
{
   protected File output;
   protected List<TestResult> results;
   protected Log log;
   protected ClusterConfig clusterConfig;
   protected Map<String, String> params;

   public void setConfigParams(Map<String, String> params)
   {
      this.params = params;
   }

   public void setOutputFile(String fileName)
   {
      this.output = new File(getFileName(fileName));
   }

   private String getFileName(String fileName)
   {
      if (fileName.indexOf("-generic-") >= 0)
      {
         return "data_" + params.get("cacheProductName") + "_" + params.get("config") + "_" + params.get("clusterSize") + ".csv";
      }
      log.info("Filename for report generation is: " + fileName);
      return fileName;
   }

   public void setResults(List<TestResult> results)
   {

      this.results = new ArrayList(results);
   }

   public void setClusterConfig(ClusterConfig clusterConfig)
   {
      this.clusterConfig = clusterConfig;
   }
}
