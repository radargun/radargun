package org.cachebench.tests.simpletests;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.cachebench.CacheWrapper;
import org.cachebench.config.Configuration;
import org.cachebench.config.TestCase;
import org.cachebench.config.TestConfig;
import org.cachebench.probes.MemoryFootprintProbe;
import org.cachebench.tests.AbstractCacheTest;
import org.cachebench.tests.StatisticTest;
import org.cachebench.tests.results.StatisticTestResult;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public abstract class SimpleTest extends AbstractCacheTest implements StatisticTest
{
   protected Log log = LogFactory.getLog(this.getClass());
   protected AtomicLong numberOfBytesPut = new AtomicLong(0);
   protected int writePercentage = 50;
   protected ExecutorService executor;
   private static final int EXECUTOR_SHUTDOWN_TIMEOUT_POLL_SECS = 60;
   protected Configuration configuration;
   protected final int LOG_FREQUENCY = 5000;
   private StatisticTestResult results;

   private int getWritePercentageFromConfig(String testCaseName, String testName)
   {
      try
      {
         TestCase tc = configuration.getTestCase(testName);
         TestConfig t = tc.getTest(testCaseName);
         return t.getIntValue("writePercentage");
      }
      catch (Exception e)
      {
         log.warn("Unable to get write percentage.  Using default (50)", e);
         return 50;
      }
   }

   public void doCumulativeTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads, StatisticTestResult str) throws Exception
   {
      this.results = str;
      doTest(testName, cache, testCaseName, sampleSize, numThreads);
   }

   protected StatisticTestResult performTestWithObjectType(String testCaseName, CacheWrapper cache, Class valueClass, String testName, int sampleSize, int numThreads) throws Exception
   {
      writePercentage = getWritePercentageFromConfig(testCaseName, testName);
      log.info("Using write percentage " + writePercentage);
      log.info("Number of threads " + numThreads);
      executor = Executors.newFixedThreadPool(numThreads);

      StatisticTestResult result = results == null ? new StatisticTestResult() : results;
      result.setTestName(testCaseName + getNodeIndex());
      result.setTestTime(new Date());
      result.setTestType(testName);

      log.info("Performing test");
      doGetsAndPuts(cache, valueClass, sampleSize, result.getGetData(), result.getPutData());

      result.setTestPassed(true); // The test is passed. The report would make use of this attribute.

      // calculate throughput, in transactions per second.
      // we only measure put operations for throughput.

      // calc tps.

      long elapsedSecondsForAllPuts = TimeUnit.NANOSECONDS.toSeconds((long) result.getPutData().getSum());

      System.out.println("*** sum of time: " + elapsedSecondsForAllPuts);
      System.out.println("*** num puts occured: " + result.getPutData().getN());

      try
      {
         result.setThroughputTransactionsPerSecond((int) (sampleSize / elapsedSecondsForAllPuts));
      }
      catch (ArithmeticException ae)
      {
         log.warn("Divide by 0 - elapsedSecondsForAllPuts = 0?");
         result.setThroughputTransactionsPerSecond(-999);
      }

      try
      {
         result.setThroughputBytesPerSecond((int) (numberOfBytesPut.longValue() / elapsedSecondsForAllPuts));
      }
      catch (ArithmeticException ae)
      {
         log.warn("Divide by 0 - elapsedSecondsForAllPuts = 0?");
         result.setThroughputBytesPerSecond(-999);
      }

      // set the number of members in the cluster
      result.setNumMembers(cache.getNumMembers());
      result.setNumThreads(numThreads);
      System.gc();
      result.addFinalMemoryUsed(MemoryFootprintProbe.calculateMemoryFootprint());
      return result;
   }

   /**
    * @param cache      The Cachewrapper on which the bechmark is conducted.
    * @param sampleSize The size of the cache.
    * @return The Descriptive statistics of the cache benchmarking.
    */
   private void doGetsAndPuts(final CacheWrapper cache, final Class valueClass, int sampleSize, final DescriptiveStatistics getStats, final DescriptiveStatistics putStats) throws Exception
   {
      log.debug("Inside doGets for : " + cache);
      final String key = "baseKey";
      final Random rand = new Random();
      int modDivisor = 100 / writePercentage;
      numberOfBytesPut.set(0);

      final AtomicInteger getCount = new AtomicInteger(0);
      final AtomicInteger putCount = new AtomicInteger(0);

      for (int i = 0; i < sampleSize; i++)
      {
         Runnable r;
         if (rand.nextInt(100) % modDivisor == 0)
         {
            // do a WRITE!
            r = new Runnable()
            {
               public void run()
               {
                  int cycleNumber = putCount.getAndIncrement();
                  try
                  {
                     // generate some value
                     Object value;
                     if (valueClass == null) value = null;
                     else if (valueClass.getName().equals(String.class.getName())) value = "value" + cycleNumber;
                     else if (valueClass.getName().equals(Integer.class.getName())) value = cycleNumber;
                     else value = valueClass.newInstance();

                     // even though some impls may use special marshalling to reduce the amount of data transmitted (such as JBoss Cache's
                     // CacheMarshaller) we still want to measure the serialized size of objects for this metric.

                     numberOfBytesPut.getAndAdd(calculateSerializedSize(value));

                     List<String> path = generatePath(key, cycleNumber);
                     String attributeKey = Integer.toHexString(rand.nextInt());

                     // start your timer...
                     boolean transactional = configuration.isUseTransactions();
                     long startTime = System.nanoTime();
                     if (transactional) cache.startTransaction();
                     cache.put(path, attributeKey, value);
                     if (transactional) cache.endTransaction(true);
                     long statValue = (System.nanoTime() - startTime);
                     putStats.addValue(statValue);
                     logOperation(cycleNumber, "PUTS", statValue);
                  }
                  catch (Exception e)
                  {
                     if (configuration.isUseTransactions()) cache.endTransaction(false);
                     // how should we handle this?  Log for now...
                     log.error("Operation failed!", e);
                  }
               }
            };
         }
         else
         {
            // do a READ!
            r = new Runnable()
            {
               public void run()
               {
                  int cycleNumber = getCount.getAndIncrement();
                  try
                  {
                     List<String> path = generatePath(key, cycleNumber);
                     String attributeKey = path.toString() + key + cycleNumber;

                     // start your timer...
                     boolean transactional = configuration.isUseTransactions();
                     long startTime = System.nanoTime();
                     if (transactional) cache.startTransaction();
                     cache.get(path, attributeKey);
                     if (transactional) cache.endTransaction(true);
                     long statValue = (System.nanoTime() - startTime);
                     getStats.addValue(statValue);
                     logOperation(cycleNumber, "GETS", statValue);
                  }
                  catch (Exception e)
                  {
                     if (configuration.isUseTransactions()) cache.endTransaction(false);
                     // how should we handle this?  Log for now...
                     log.error("Operation failed!", e);
                  }
               }
            };
         }

         // submit task to be executed
         executor.execute(r);
      }

      // only leave once the task queue is empty!!
      blockTillTasksComplete();

      // return the raw data
      log.debug("Leaving doTasks for : " + cache);
   }

   private void logOperation(int i, String s, long statvalue)
   {
      if ((i + 1) % LOG_FREQUENCY == 0)
      {
         log.info((i + 1) + " " + s + " were performed");
         if (log.isTraceEnabled()) log.trace("Latest " + s + " took " + statvalue);
      }
   }

   private void blockTillTasksComplete()
   {
      // now that just told us that all the tasks have been submitted.  Lets check that the executor has finished everything.
      executor.shutdown();
      while (!executor.isTerminated())
      {
         try
         {
            executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_POLL_SECS, TimeUnit.SECONDS);
         }
         catch (InterruptedException e)
         {
            // do nothing?
         }
      }
   }

   private long calculateSerializedSize(Object value)
   {
      ByteArrayOutputStream baos = null;
      ObjectOutputStream oos = null;
      try
      {
         baos = new ByteArrayOutputStream();
         oos = new ObjectOutputStream(baos);
         oos.writeObject(value);
         oos.close();
         baos.close();
         return baos.size();
      }
      catch (Exception e)
      {
         log.warn("Unable to calculate serialized size of object " + value, e);
         try
         {
            if (oos != null) oos.close();
            if (baos != null) baos.close();
         }
         catch (Exception e2)
         {
            log.warn("Unable to close streams", e2);
         }
      }
      return 0;
   }

   public String getNodeIndex()
   {
      try
      {
         return " - " + Integer.parseInt(System.getProperty("currentIndex"));
      }
      catch (Exception e)
      {
         return "";
      }
   }

   public void setConfiguration(Configuration configuration)
   {
      this.configuration = configuration;
   }
}
