package org.cachebench.tests.simpletests;

import org.cachebench.testobjects.CustomTypeWithStatics;


/**
 * This is a test on the time taken to add and get a custom object to and from the cache with aggregated static objects within it.
 * There are two differnet custom objects that could be used based on the wrapper in use. If the wrapper is a serializable
 * wrapper, then the SerializableCustomTypeWithStatics object would be used. If the wrapper is not a serializable wrapper, then
 * CustomTypeWithStatics object would be used.
 * Refer JavaDocs for SerializableCustomTypeWithStatics and CustomTypeWithStatics class structures.
 *
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class StaticsTest extends SimpleTest
{
   @Override
   protected Object generateValue(int iteration) {
      return new CustomTypeWithStatics(iteration);
   }
}
