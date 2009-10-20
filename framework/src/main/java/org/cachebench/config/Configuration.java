package org.cachebench.config;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Manik Surtani (manik@surtani.org)
 * @version $Id: Configuration.java,v 1.3 2007/04/18 19:09:31 msurtani Exp $
 */
public class Configuration
{
   private int maxTreeDepth;
   private int sampleSize;
   private int sleepBetweenTests;
   private boolean gcBetweenTestsEnabled;
   private boolean emptyCacheBetweenTests;
   private boolean localOnly;
   private boolean useTransactions;

   private ClusterConfig clusterConfig;

   private List<TestCase> testCases = new ArrayList<TestCase>();

   private List<Report> reports = new ArrayList<Report>();
   private int numThreads;

   public boolean isLocalOnly()
   {
      return localOnly;
   }

   public void setLocalOnly(boolean localOnly)
   {
      this.localOnly = localOnly;
   }

   public int getSampleSize()
   {
      return sampleSize;
   }

   public void setSampleSize(int sampleSize)
   {
      this.sampleSize = sampleSize;
   }

   public boolean isGcBetweenTestsEnabled()
   {
      return gcBetweenTestsEnabled;
   }

   public void setGcBetweenTestsEnabled(boolean gcBetweenTestsEnabled)
   {
      this.gcBetweenTestsEnabled = gcBetweenTestsEnabled;
   }

   public int getSleepBetweenTests()
   {
      return sleepBetweenTests;
   }

   public void setSleepBetweenTests(int sleepBetweenTests)
   {
      this.sleepBetweenTests = sleepBetweenTests;
   }

   public boolean isEmptyCacheBetweenTests()
   {
      return emptyCacheBetweenTests;
   }

   public void setEmptyCacheBetweenTests(boolean emptyCacheBetweenTests)
   {
      this.emptyCacheBetweenTests = emptyCacheBetweenTests;
   }

   public List<Report> getReports()
   {
      return reports;
   }

   public List<TestCase> getTestCases()
   {
      return testCases;
   }

   public void addTestCase(TestCase testCase)
   {
      testCases.add(testCase);
   }

   public void addReport(Report report)
   {
      reports.add(report);
   }

   public int getNumThreads()
   {
      return numThreads;
   }

   public void setNumThreads(int numThreads)
   {
      this.numThreads = numThreads;
   }

   public ClusterConfig getClusterConfig()
   {
      return clusterConfig;
   }

   public void setClusterConfig(ClusterConfig clusterConfig)
   {
      this.clusterConfig = clusterConfig;
   }

   public int getMaxTreeDepth()
   {
      return maxTreeDepth;
   }

   public void setMaxTreeDepth(int maxTreeDepth)
   {
      this.maxTreeDepth = maxTreeDepth;
   }

   public boolean isUseTransactions()
   {
      return useTransactions;
   }

   public void setUseTransactions(boolean useTransactions)
   {
      this.useTransactions = useTransactions;
   }

   public TestCase getTestCase(String testCaseName)
   {
      for (TestCase testCase : getTestCases())
      {
         if (testCaseName.equalsIgnoreCase(testCase.getName()))
         {
            return testCase;
         }
      }
      return null;
   }
}
