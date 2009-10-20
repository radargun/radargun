package org.cachebench.tests;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractCacheTest implements CacheTest
{
   /**
    * @return a well-spaced path for a key-value pair
    */
   protected List<String> generatePath(String base, int sequence)
   {
      // use bucket sizes of 100 and a depth of 3.
      int intermediate = sequence % 100;
      return Arrays.asList(base, "Intermediate-" + intermediate, "Node " + sequence);
   }

}
