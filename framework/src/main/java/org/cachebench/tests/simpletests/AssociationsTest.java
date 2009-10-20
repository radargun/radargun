package org.cachebench.tests.simpletests;

import org.cachebench.CacheWrapper;
import org.cachebench.testobjects.CustomTypeWithAssocs;
import org.cachebench.tests.results.StatisticTestResult;


/**
 * This is a test on the time taken to add and get a custom object to and from the cache with aggregated objects within it.
 * There are two differnet custom objects that could be used based on the wrapper in use. If the wrapper is a serializable
 * wrapper, then the SerializableCustomTypeWithAssocs object would be used. If the wrapper is not a serializable wrapper, then
 * CustomTypeWithAssocs object would be used.
 * Refer JavaDocs for SerializableCustomTypeWithAssocs and CustomTypeWithAssocs class structures.
 *
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class AssociationsTest extends SimpleTest
{

   /* (non-Javadoc)
   * @see org.cachebench.tests.CacheTest#doTest(org.cachebench.config.TestConfig)
   */
   public StatisticTestResult doTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads) throws Exception
   {
      return performTestWithObjectType(testName, cache, CustomTypeWithAssocs.class, testCaseName, sampleSize, numThreads);

   }

}
