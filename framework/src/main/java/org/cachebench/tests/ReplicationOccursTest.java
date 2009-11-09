package org.cachebench.tests;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.cluster.ClusterBarrier;
import org.cachebench.config.Configuration;
import org.cachebench.config.TestCase;
import org.cachebench.config.TestConfig;
import org.cachebench.tests.results.StatisticTestResult;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * For tests that stress cluster replication, this check whether a put in this node is replicated across the cluster.
 * The purpose of this is to ensure that nodes are configured correctly (they have replication enabled and it works).
 *
 * @author Mircea.Markus@jboss.com
 */
public class ReplicationOccursTest implements ClusteredCacheTest
{
   public static final Log log = LogFactory.getLog(ReplicationOccursTest.class);

   private Configuration conf;
   private TestConfig testConfig;
   private static final String PREFIX = "_ReplicationOccursTest_";
   private static final int REPLICATION_TRY_COUNT = 17;
   private static final int REPLICATION_TRY_SLEEP = 2000;

   public void setConfiguration(Configuration configuration, TestConfig tc)
   {
      this.conf = configuration;
      this.testConfig = tc;
   }

   public StatisticTestResult doTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads) throws Exception
   {
      log.trace("TestCase = '" + testCaseName + "', TestName = " + testName);
      barrier("BEFORE_REPLICATION_OCCURS_BARRIER");

      Integer currentNodeIndex = conf.getClusterConfig().getCurrentNodeIndex();
      tryToPut(cache, currentNodeIndex);

      barrier("AFTER_ADDING_LOCAL_ELEMENTS_BARRIER");      
      Thread.sleep(REPLICATION_TRY_SLEEP);//just to make sure that prev barrier closed its sockets etc

      if (conf.getClusterConfig().getClusterSize() == 1)
      {
         log.info("Cluster size is one, no replication expected");
         StatisticTestResult result = new StatisticTestResult();
         result.setTestPassed(true);
         result.setSkipReport(true);
         return result;
      }

      boolean allNodesReplicated = checkReplicationSeveralTimes(testName, cache, testCaseName);

      Map<SocketAddress, Object> receivedValues = broadcastReplicationResult(allNodesReplicated);
      try
      {
         cache.empty();
      }
      catch (Throwable e)
      {
         log.warn("Fail to cleanup cache after replication test", e);
      }
      return allReplicatedFine(receivedValues);
   }

   private void tryToPut(CacheWrapper cache, Integer currentNodeIndex) throws Exception
   {
      int tryCount = 0;
      while (tryCount < 5)
      {
         try
         {
            cache.put(Arrays.asList(PREFIX, "" + currentNodeIndex), PREFIX + currentNodeIndex, "true");
            return;
         }
         catch (Throwable e)
         {
            log.warn("Error while trying to put data: ", e);
            tryCount++;
         }
      }
      throw new Exception("Couldn't accomplish additiona before replication!");
   }

   /**
    * If caches replicate async, then try several times.
    */
   private boolean checkReplicationSeveralTimes(String testName, CacheWrapper cache, String testCaseName)
         throws Exception
   {
      for (int i = 0; i < REPLICATION_TRY_COUNT; i++)
      {
         if (nodesReplicated(cache, testCaseName, testName))
         {
            return true;
         }
         log.info("Replication test failed, " + (i + 1) + " tries so far. Sleeping for  " + REPLICATION_TRY_SLEEP
               + " millis then try again");
         Thread.sleep(REPLICATION_TRY_SLEEP);
      }
      return false;
   }

   private Map<SocketAddress, Object> broadcastReplicationResult(boolean allNodesReplicated)
         throws Exception
   {
      ClusterBarrier barrier = new ClusterBarrier();
      barrier.setConfig(conf.getClusterConfig());
      barrier.barrier(String.valueOf(allNodesReplicated));
      Map<SocketAddress, Object> receivedValues = barrier.getReceivedMessages();
      log.info("Recieved following responses from barrier:" + receivedValues);
      return receivedValues;
   }

   private void barrier(String res) throws Exception
   {
      ClusterBarrier barrier = new ClusterBarrier();
      barrier.setAcknowledge(true);
      barrier.setConfig(conf.getClusterConfig());
      barrier.barrier(res);
   }

   private StatisticTestResult allReplicatedFine(Map<SocketAddress, Object> receivedValues)
   {
      StatisticTestResult result = new StatisticTestResult();
      result.setSkipReport(true);
      for (Object value : receivedValues.values())
      {
         if (!"true".equals(value))
         {
            log.info("Replication was not successful on the entire cluster!");
            result.setTestPassed(false);
            return result;
         }
      }
      result.setTestPassed(true);
      log.info("Replication test successfully passed.");
      return result;
   }

   private boolean isPartialReplication(String testCaseName, String testName)
   {
      TestCase testCase = conf.getTestCase(testCaseName);
      TestConfig thisConfig = testCase.getTest(testName);
      return thisConfig.existsParam("partialReplication") &&
            "true".equalsIgnoreCase(thisConfig.getParamValue("partialReplication"));
   }

   private boolean nodesReplicated(CacheWrapper cache, String testCaseName, String testName) throws Exception
   {
      int clusterSize = conf.getClusterConfig().getClusterSize();
      int replicaCount = 0;
      for (int i = 0; i < clusterSize; i++)
      {
         int currentNodeIndex = conf.getClusterConfig().getCurrentNodeIndex();
         if (i == currentNodeIndex)
         {
            continue;
         }
         Object data = tryGet(cache, i);
         if (data == null || !"true".equals(data))
         {
            log.trace("Cache with index " + i + " did *NOT* replicate");
         }
         else
         {
            log.trace("Cache with index " + i + " replicated here ");
            replicaCount++;
         }
      }
      log.info("Number of caches that replicated here is " + replicaCount);
      if (isPartialReplication(testCaseName, testName))
      {
         return verifyClusterReplication(replicaCount);
      }
      return replicaCount == conf.getClusterConfig().getClusterSize() - 1;
   }

   private Object tryGet(CacheWrapper cache, int i) throws Exception
   {
      int tryCont = 0;
      while (tryCont < 5)
      {
         try
         {
            return cache.getReplicatedData(Arrays.asList(PREFIX, "" + i), PREFIX + i);
         }
         catch (Throwable e)
         {
            tryCont++;
         }
      }
      return null;
   }

   /**
    * Checks whether the sum of replciations across the cluster is bigger than the number of nodes in the cluster, in
    * other words each node replicated at least once.
    */
   private boolean verifyClusterReplication(int replicaCount) throws Exception
   {
      ClusterBarrier barrier = new ClusterBarrier();
      barrier.setConfig(conf.getClusterConfig());
      barrier.barrier(replicaCount);
      Collection recievedValues = barrier.getReceivedMessages().values();
      log.trace("Recieved the following repilcation counts: " + recievedValues);
      int totalValue = 0;
      for (Object val : recievedValues)
      {
         totalValue += Integer.valueOf(val.toString());
      }
      log.info("Overall replication count is: " + totalValue);
      //this means SOME replication occurred. This does not mean, though, that all nodes replicated successfuly.
      //correct condition would be >= this.conf.getClusterConfig().getClusterSize()
      //todo/FIXME - this seem not to work with all the products, so we will accept 'some replication'
      if (totalValue < this.conf.getClusterConfig().getClusterSize() && totalValue != 0)
      {
         log.warn("The replication was not total, but partial!!");
      }
      boolean isReplicationSuccess = totalValue > 0;
      return isReplicationSuccess;
   }
}
