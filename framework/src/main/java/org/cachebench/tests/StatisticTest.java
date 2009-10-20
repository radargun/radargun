package org.cachebench.tests;

import org.cachebench.CacheWrapper;
import org.cachebench.tests.results.StatisticTestResult;

/**
 * Marker interface
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 */
public interface StatisticTest
{
   void doCumulativeTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads, StatisticTestResult str) throws Exception;
}
