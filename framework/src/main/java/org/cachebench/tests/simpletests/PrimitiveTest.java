package org.cachebench.tests.simpletests;

/**
 * This is a simple test of adding and getting java.lang.Integer objects in and out of the cache.
 * 
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class PrimitiveTest extends SimpleTest
{
   @Override
   protected Object generateValue(int iteration) {
      return iteration;
   }
}
