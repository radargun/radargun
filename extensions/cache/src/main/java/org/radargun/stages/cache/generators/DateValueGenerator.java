package org.radargun.stages.cache.generators;

import java.util.Date;
import java.util.Random;
import org.radargun.config.DefinitionElement;

/**
 * @author Roman Macor (rmacor@redhat.com)
 */
@DefinitionElement(name = "date", doc = "Generates random Date values")
public class DateValueGenerator implements ValueGenerator {
   @Override
   public Object generateValue(Object key, int size, Random random) {
      return new Date(random.nextLong());
   }

   @Override
   public int sizeOf(Object value) {
      return sizeOf(value);
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      return value instanceof Date;
   }
}
