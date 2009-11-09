package org.cachebench.tests.simpletests;

import org.cachebench.testobjects.CustomTypeWithTransient;


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
   @Override
   protected Object generateValue(int iteration) {
      return new CustomTypeWithTransient(iteration);
   }
}
