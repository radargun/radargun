package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.DefinitionElement;

/**
 * Surrounds the word with nonsense. Use for wildcard queries.
 */
@DefinitionElement(name = "word-in-haystack", doc = "Generates text-objects with string with single randomly picked word surrounded by another characters.")
public class WordInHaystackGenerator extends DictionaryTextObjectGenerator {
   private static final char[] ALPHABET = "abcdefghijklmnopqrstuvw 1234567890".toCharArray();

   @Override
   public Object generateValue(Object key, int size, Random random) {
      String word = dictionary[random.nextInt(dictionary.length)];
      StringBuilder sb = new StringBuilder(size);
      if (word.length() < size) {
         int position = random.nextInt(size - word.length());
         for (int i = position; i > 0; --i) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      }
      if (word.length() <= size) {
         sb.append(word);
      }
      while (sb.length() < size) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      return newInstance(sb.toString());
   }
}
