package org.cachebench.tests.simpletests;

import java.util.Random;

/**
 * This is a simple test of adding and getting java.lang.String objects in and out of the cache.
 *
 * @author Manik Surtani (manik@surtani.org)
 *         (C) Manik Surtani, 2004
 */
public class StringTest extends SimpleTest
{
   @Override
   protected Object generateValue(int iteration) {
      // how big a string do we want?!?
      // each char is 2 bytes.
      StringBuilder sb = new StringBuilder("");
      Random r = new Random();
      for (int i=0; i<testConfig.getPayloadSizeInBytes() / 2; i++)
         sb.append(Integer.toString(r.nextInt(36), 36));

      return sb.toString();
   }
}
