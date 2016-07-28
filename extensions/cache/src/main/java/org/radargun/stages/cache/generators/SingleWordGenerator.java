package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.DefinitionElement;

/**
 * Just picks single word from the dictionary, ignoring size constraints.
 */
@DefinitionElement(name = "single-word", doc = "Generates text-objects with single randomly picked word.")
public class SingleWordGenerator extends DictionaryTextObjectGenerator {
   @Override
   public Object generateValue(Object key, int size, Random random) {
      return newInstance(dictionary[random.nextInt(dictionary.length)]);
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      return getText(value) != null;
   }
}
