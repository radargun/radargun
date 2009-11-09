package org.cachebench.tests.simpletests;

import org.cachebench.testobjects.CustomTypeWithAssocs;


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
   @Override
   protected Object generateValue(int iteration) {
      return new CustomTypeWithAssocs(iteration);
   }
}
