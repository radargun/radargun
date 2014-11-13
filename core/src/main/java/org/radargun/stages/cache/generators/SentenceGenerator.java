package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.DefinitionElement;

/**
 * Generates sentence made up from words in the dictionary.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "sentence", doc = "Generates text-objects with string from randomly picked words.")
public class SentenceGenerator extends DictionaryTextObjectGenerator {
   @Override
   public Object generateValue(Object key, int size, Random random) {
      StringBuilder sb = new StringBuilder(size);
      while (sb.length() < size) {
         String word = dictionary[random.nextInt(dictionary.length)];
         if (word.length() + sb.length() < size) {
            sb.append(word);
         } else {
            for (int i = sb.length(); i < size; ++i) sb.append(' ');
         }
         if (sb.length() < size) {
            sb.append(' ');
         }
      }
      return newInstance(sb.toString());
   }
}
