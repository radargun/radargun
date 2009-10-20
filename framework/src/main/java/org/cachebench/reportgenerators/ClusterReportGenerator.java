package org.cachebench.reportgenerators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.cluster.ClusterBarrier;
import org.cachebench.tests.results.TestResult;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gathers info from all the nodes executing tests.
 * Merges all the gathered information and generates an CSV file.
 * The file is generated on the master node, i.e. the node that has the NODE_INDEX == 0
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClusterReportGenerator extends AbstractReportGenerator implements ClusterAwareReportGenerator
{
   private static Log log = LogFactory.getLog(ClusterReportGenerator.class);
   private String reportGeneratorClassName;

   @Override
   public void setConfigParams(Map<String, String> configParams)
   {
      super.setConfigParams(configParams);
      log.trace("Received config params: " + configParams);
      reportGeneratorClassName = configParams.get("generatorClassName");
   }

   public void generate() throws Exception
   {
      try
      {
         ClusterBarrier barrier = new ClusterBarrier();
         barrier.setConfig(this.clusterConfig);
         barrier.setAcknowledge(false);
         barrier.barrier(results);

         log.trace(" Starting generating. Is master? " + clusterConfig.isMaster());
         if (clusterConfig.isMaster())
         {
            log.info("Master node, generating report");
            log.trace("Received following results: " + results);
            generateReport(barrier.getReceivedMessages());
         }
      }
      catch (Exception e)
      {
         log.error("Error while generating report!", e);
      }
      finally
      {
         barrierUntilReportIsGenerated();
      }
   }

   private void barrierUntilReportIsGenerated() throws Exception
   {
      ClusterBarrier barrier = new ClusterBarrier();
      barrier.setConfig(this.clusterConfig);
      barrier.setAcknowledge(true);
      barrier.barrier("AFTER_REPORT_GENERATED_BARRIER");
   }


   private void generateReport(Map<SocketAddress, Object> receivedMessages) throws Exception
   {
      log.trace("Received " + receivedMessages.size() + " results!");
      List<List<TestResult>> results = new ArrayList<List<TestResult>>();
      for (SocketAddress socketAddress : receivedMessages.keySet())
      {
         Object obj = receivedMessages.get(socketAddress);
         if (!(obj instanceof List))
         {
            log.error("Expected a List of results, but received '" + obj + "'");
            throw new IllegalStateException("Expected a List of results, but received '" + obj + "'");
         }
         List<TestResult> testResults = (List<TestResult>) obj;
         log.trace("From " + socketAddress + " received " + testResults);
         results.add(testResults);
      }
      List<TestResult> mergedResults = mergerTestResultsAndGenerateReport(results);
      generateReportFile(mergedResults);
   }

   private void generateReportFile(List<TestResult> mergedResults) throws Exception
   {
      if (mergedResults.isEmpty())
      {
         log.warn("Result list is emty, not generating any report");
         return;
      }
      String genClassName = mergedResults.get(0).getReportGeneratorClassName();
      AbstractReportGenerator generator = instantiateReportGenerator(genClassName);
      generator.setResults(mergedResults);
      log.debug("Generating reports to file: " + output);
      generator.output = output;
      generator.generate();
   }

   private AbstractReportGenerator instantiateReportGenerator(String genClassName)
   {
      try
      {
         log.debug("Using generator class: " + genClassName);
         return (AbstractReportGenerator) Class.forName(genClassName).newInstance();
      }
      catch (Exception e)
      {
         log.error("Could not instantiate report generators", e);
         throw new IllegalStateException(e);
      }
   }

   private List<TestResult> mergerTestResultsAndGenerateReport(List<List<TestResult>> results)
   {
      List<TestResult> mergedResults = new ArrayList<TestResult>();
      for (int i = 0; i < results.get(0).size(); i++)
      {
         for (int j = 0; j < results.size(); j++)
         {
            //if one servers sent us more results that the other(s) fail!
            if (j > 0 && results.get(j).size() != results.get(j - 1).size())
            {
               //not effcient as it gets execute each time but not really matters
               throw new RuntimeException("Not all servers send the same number of responses. I.e. not all severs run the same number of tests!!!");
            }
            mergedResults.add(results.get(j).get(i));
         }
      }
      log.trace("Merged tests are: " + mergedResults);
      return mergedResults;
   }

}
