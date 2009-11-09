package org.cachebench.tests.simpletests;

import org.cachebench.testobjects.CustomType;


/**
 * This is a test on the time taken to add and get a custom object to and from the cache.
 * There are two differnet custom objects that could be used based on the wrapper in use. If the wrapper is a serializable
 * wrapper, then the SerializableCustomType object would be used. If the wrapper is not a serializable wrapper, then
 * CustomType object would be used.
 *
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class CustomClassTest extends SimpleTest
{
   @Override
   protected Object generateValue(int iteration) {
      return new CustomType(iteration);
   }
}
