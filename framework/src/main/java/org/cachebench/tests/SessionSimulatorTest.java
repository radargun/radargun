package org.cachebench.tests;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.config.Configuration;
import org.cachebench.config.TestConfig;
import org.cachebench.tests.results.TestResult;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates work with a web session.
 * <p/>
 * todo mmarkus - improve the test to support multiple threads. Correlate this with usage of message_bundling in JBC
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class SessionSimulatorTest extends AbstractCacheTest
{
   private static final Log log = LogFactory.getLog(SessionSimulatorTest.class);

   public static final String SESSION_PREFIX = "SESSION_";
   private static int LOG_AFTER_OPERATION_COUNT = 5000;
   Configuration configuration;
   private TestConfig thisTestConfig;
   private String sessionId;
   private CacheWrapper cacheWrapper;
   private boolean reportNanos = false;
   private boolean registerReplicationDelay = false;


   /**
    * total number of request to be made against this session: reads + writes
    */
   private int numberOfRequests;

   /**
    * for each session there will be created fixed number of attributes. On those attributes all the GETs and PUTs
    * are performed (for PUT is overwrite)
    */
   private int numberOfAttributes;

   /**
    * Each attribute will be a byte[] of this size
    */
   private int sizeOfAnAttribute;

   /**
    * Out of the total number of request, this define the frequency of writes (percentage)
    */
   private int writePercentage;
   private ExecutorService ex;

   public void setConfiguration(Configuration configuration, TestConfig tc)
   {
      this.configuration = configuration;
      this.thisTestConfig = tc;
   }

   public TestResult doTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads) throws Exception
   {
      ex = Executors.newFixedThreadPool(configuration.getNumThreads());
      this.cacheWrapper = cache;
      readParams();
      initSession();
      return stressSession(testName, testCaseName);
   }

   private SessionSimulatorTestResult stressSession(String testName, String testCaseName) throws Exception
   {

      long totalBytesRead = 0;
      long totalBytesWritten = 0;
      long reads = 0;
      long writes = 0;
      int readPercentage = 100 - writePercentage;
      Random r = new Random();
      int randomAction;
      int randomAttribute;
      AtomicLong durationNanos = new AtomicLong(0);
      for (int i = 0; i < numberOfRequests; i++)
      {
         logRunCount(i);
         randomAction = r.nextInt(100);
         randomAttribute = r.nextInt(numberOfAttributes - 1);
         byte[] buf;

         String sessionEntry = getSessionEntry(randomAttribute);
         List<String> path = generatePath(sessionId, randomAttribute);

         if (randomAction < readPercentage)
         {
            // read
            ex.execute(new ReadTask(path, durationNanos, sessionEntry));
            reads++;
         }
         else
         {
            // write
            ex.execute(new WriteTask(path, durationNanos, sessionEntry));
            writes++;
         }
      }

      ex.shutdown();
      ex.awaitTermination(60 * 10, TimeUnit.SECONDS);

//      long replicationDelay = System.currentTimeMillis();
//      replicationDelay = verifyReplicationDelay(replicationDelay);

      long duration = this.reportNanos ? durationNanos.get() : durationNanos.get() / 1000000;
      SessionSimulatorTestResult result = new SessionSimulatorTestResult(reads, writes, duration, totalBytesRead, totalBytesWritten);
      result.setTestPassed(true);
      result.setTestName(testCaseName + getNodeIndex());
      result.setTestTime(new Date());
      result.setTestType(testName);
//      if (registerReplicationDelay)
//      {
//         result.setReplicationDelayMillis(replicationDelay);
//      }
      log.trace("Returning result:" + result);
      return result;
   }

   private long verifyReplicationDelay(long replicationDelay)
         throws Exception
   {
      if (registerReplicationDelay)
      {
         log.info("Gathering replication delay");
         String key = "registerReplicationDelay";
         List<String> path = Arrays.asList(key);
         int clusterSize = configuration.getClusterConfig().getClusterSize();
         while (notAllAck(path, cacheWrapper))
         {
            cacheWrapper.put(path, key + getNodeIndex(), getNodeIndex());
            for (int i = 0; i < clusterSize; i++)
            {
               Object replicatedValue = cacheWrapper.get(path, key + i);
               if (log.isTraceEnabled()) log.trace("replication delay value from node " + i + " is " + replicatedValue);
               if (replicatedValue != null)
               {
                  cacheWrapper.put(path, buildAckKey(Integer.parseInt(String.valueOf(replicatedValue)), getNodeIndex()), "recieved");
               }
            }
            //todo mmarkus this brings a delay of 1 sec to the async replication config
            Thread.sleep(1000);
         }
         replicationDelay = System.currentTimeMillis() - replicationDelay;
         log.info("Replication delay is " + replicationDelay + " millis.");
      }
      return replicationDelay;
   }

   private String buildAckKey(int sender, int receiver)
   {
      return sender + "->" + receiver;
   }

   private boolean notAllAck(List path, CacheWrapper cacheWrapper) throws Exception
   {
      for (int i = 0; i < configuration.getClusterConfig().getClusterSize(); i++)
      {
         for (int j = 0; j < configuration.getClusterConfig().getClusterSize(); j++)
         {
            if (cacheWrapper.get(path, buildAckKey(i, j)) == null)
            {
               if (log.isTraceEnabled()) log.trace("Missing replication message: " + buildAckKey(i, j));
               return true;
            }
         }
      }
      return false;
   }

   private void logRunCount(int i)
   {
      if (((i + 1) % LOG_AFTER_OPERATION_COUNT == 0) || (i == 0))
      {
         log.info("SessionSimulatorTest performed " + (i == 0 ? 0 : (i + 1)) + " operations");
      }
   }

   private void initSession() throws Exception
   {
      for (int i = 0; i < numberOfAttributes; i++)
      {
         try
         {
            cacheWrapper.put(generatePath(sessionId, i), getSessionEntry(i), new byte[sizeOfAnAttribute]);
         }
         catch (Throwable e)
         {
            log.warn("Error while initializing the session: ", e);
         }
      }
   }

   private String getSessionEntry(int i)
   {
      return sessionId + i;
   }

   private void readParams()
   {
      sessionId = SESSION_PREFIX + getNodeIndex();
      log.debug("Session id is: " + sessionId);
      numberOfRequests = thisTestConfig.getIntValue("numberOfRequest");
      numberOfAttributes = thisTestConfig.getIntValue("numberOfAttributes");
      writePercentage = thisTestConfig.getIntValue("writePercentage");
      sizeOfAnAttribute = thisTestConfig.getIntValue("sizeOfAnAttribute");
      if (thisTestConfig.existsParam("reportNanos"))
      {
         this.reportNanos = thisTestConfig.getBooleanValue("reportNanos");
      }
      if (thisTestConfig.existsParam("registerReplicationDelay"))
      {
         this.registerReplicationDelay = thisTestConfig.getBooleanValue("registerReplicationDelay");
      }
      log.debug("recieved follosing params[ numberOfRequests=" + numberOfRequests + ", numberOfAttributes="
            + numberOfAttributes + ", writePercentage=" + writePercentage + ", sizeOfAnAttribute=" + sizeOfAnAttribute + " ]");
   }

   public int getNodeIndex()
   {
      return configuration.isLocalOnly() ? 0 : configuration.getClusterConfig().getCurrentNodeIndex();
   }

   private abstract static class Task implements Runnable
   {
      List<String> path;
      AtomicLong durationNanos;
      Object sessionEntry;

      protected Task(List<String> path, AtomicLong durationNanos, Object sessionEntry)
      {
         this.path = path;
         this.durationNanos = durationNanos;
         this.sessionEntry = sessionEntry;
      }
   }

   private class WriteTask extends Task
   {
      protected WriteTask(List<String> path, AtomicLong durationNanos, Object sessionEntry)
      {
         super(path, durationNanos, sessionEntry);
      }

      public void run()
      {
         try
         {
            String payload = generateRandomString(sizeOfAnAttribute);
            long start = System.nanoTime();
            cacheWrapper.put(path, sessionEntry, payload);
            durationNanos.getAndAdd(System.nanoTime() - start);
//            totalBytesWritten += buf.length;
         }
         catch (Throwable e)
         {
            log.warn("Error appeared whilst writing to cache:" + e.getMessage(), e);
         }
      }
   }

   private class ReadTask extends Task
   {
      protected ReadTask(List<String> path, AtomicLong durationNanos, Object sessionEntry)
      {
         super(path, durationNanos, sessionEntry);
      }

      public void run()
      {
         try
         {
            long start = System.nanoTime();
            cacheWrapper.get(path, sessionEntry);
            durationNanos.getAndAdd(System.nanoTime() - start);
//            totalBytesRead += buf == null ? 0 : buf.length;
         }
         catch (Throwable e)
         {
            log.warn("Error appeared whilst reading from cache:" + e.getMessage(), e);
         }
      }
   }

   private static Random r = new Random();

   private static String generateRandomString(int size)
   {
      // each char is 2 bytes
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < size / 2; i++) sb.append((char) (64 + r.nextInt(26)));
      return sb.toString();
   }
}
