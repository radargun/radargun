package org.radargun.stages.cache.generators;

import java.util.Random;
import org.radargun.config.DefinitionElement;

/**
 * @author Roman Macor (rmacor@redhat.com)
 */
@DefinitionElement(name = "bool", doc = "Generates random boolean numbr")
public class BooleanValueGenerator implements ValueGenerator {

   @Override
   public Object generateValue(Object key, int size, Random random) {
      return random.nextBoolean();
   }

   @Override
   public int sizeOf(Object value) {
      return sizeOf(value);
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      return value instanceof Boolean;
   }
}
