package org.cachebench.reportgenerators;

import org.cachebench.config.ClusterConfig;
import org.cachebench.tests.results.TestResult;

import java.util.List;
import java.util.Map;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: ReportGenerator.java,v 1.4 2007/04/18 19:09:31 msurtani Exp $
 */
public interface ReportGenerator
{
   public void setConfigParams(Map<String, String> configParams);
   
   public void setOutputFile(String fileName);

   public void setResults(List<TestResult> results);

   public void generate() throws Exception;

   public void setClusterConfig(ClusterConfig clusterConfig);
}
