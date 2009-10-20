package org.cachebench.tests.simpletests;

import org.cachebench.CacheWrapper;
import org.cachebench.testobjects.CustomTypeSubclassOfAbstract;
import org.cachebench.tests.results.StatisticTestResult;


/**
 * This is a test on the time taken to add and get a object to and from the cache with. The object under test in this instance,
 * would be a sub class. Which means it would be inheriting from a parent and would have a additional attributes which will go in the Cache.
 * There two different sub class tests that would be done based on the wrapper in use. If the wrapper is a Serializable,
 * then the SerializableCustomTypeSubclassOfAbstract object would be used. If the wrapper is not serializable, then
 * the CustomTypeSubclassOfAbstract object would be used.
 *
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class SubclassTest extends SimpleTest
{

   /* (non-Javadoc)
   * @see org.cachebench.tests.CacheTest#doTest(org.cachebench.config.TestConfig)
   */
   public StatisticTestResult doTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads) throws Exception
   {
      return performTestWithObjectType(testName, cache, CustomTypeSubclassOfAbstract.class, testCaseName, sampleSize, numThreads);

   }

}
