package org.cachebench.tests.simpletests;

import org.cachebench.CacheWrapper;
import org.cachebench.tests.results.StatisticTestResult;


/**
 * This is a simple test of adding and getting java.lang.Integer objects in and out of the cache.
 * 
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class PrimitiveTest extends SimpleTest
{

   /* (non-Javadoc)
   * @see org.cachebench.tests.CacheTest#doTest(org.cachebench.config.TestConfig)
   */
   public StatisticTestResult doTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads) throws Exception
   {
      return performTestWithObjectType(testName, cache, Integer.class, testCaseName, sampleSize, numThreads);

   }

}
