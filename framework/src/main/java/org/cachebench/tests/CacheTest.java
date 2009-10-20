package org.cachebench.tests;

import org.cachebench.CacheWrapper;
import org.cachebench.config.Configuration;
import org.cachebench.tests.results.TestResult;


/**
 * Each newly creted test should extend this class.
 *
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public interface CacheTest
{

   /**
    * Called before {@link #doTest(String, org.cachebench.CacheWrapper, String, int, int)}.
    * Implemetations may cache this and further use on doTest method.
    */
   void setConfiguration(Configuration configuration);

   /**
    * Performs the benchmarking on the given tese.
    *
    * @param testName     The name of the test to be performed.
    * @param cache        The Cache wrapper for the product under bench-mark.
    * @param testCaseName The name of the test case.
    * @param sampleSize   The sample size of the cache to be tested.
    * @param numThreads   The number of concurrent threads to use to achieve the sample number of invocations
    * @return The result of the test.
    * @throws Exception When the cache opertations blow up an error.
    */
   TestResult doTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads) throws Exception;
}
