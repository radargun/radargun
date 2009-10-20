package org.cachebench.tests.simpletests;

import org.cachebench.CacheWrapper;
import org.cachebench.testobjects.CustomTypeWithTransient;
import org.cachebench.tests.results.StatisticTestResult;


/**
 * This is a test on the time taken to add and get a object to and from the cache with Transient objects. The object under test
 * in this instance would be a Object which consists of transient objects. The type of object under test would also differ based
 * on the wrapper in use. Thus if the wrapper in use is Serializable, the SerializableCustomTypeWithTransient object would be used.
 * Else if the wrapper is not serializable, the CustomTypeWithTransient object would be used.
 *
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class TransientTest extends SimpleTest
{

   /* (non-Javadoc)
   * @see org.cachebench.tests.CacheTest#doTest(org.cachebench.config.TestConfig)
   */
   public StatisticTestResult doTest(String testName, CacheWrapper cache, String testCaseName, int sampleSize, int numThreads) throws Exception
   {
      return performTestWithObjectType(testName, cache, CustomTypeWithTransient.class, testCaseName, sampleSize, numThreads);

   }
}
