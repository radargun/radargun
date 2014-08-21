package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.utils.Utils;

/**
 * Surrounds the word with nonsense. Use for wildcard queries.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class WordInHaystackGenerator extends TextObjectGenerator {
   @Property(doc = "File with words (one word per line).", optional = false)
   private String file;

   private static char[] ALPHABET = "abcdefghijklmnopqrstuvw 1234567890".toCharArray();

   private String[] dictionary;

   @Init
   public void initDictionary() {
      dictionary = Utils.readFile(file).toArray(new String[0]);
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      String word = dictionary[random.nextInt(dictionary.length)];
      int position = random.nextInt(size - word.length());
      StringBuilder sb = new StringBuilder(size);
      for (int i = position; i > 0; --i) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      sb.append(word);
      while (sb.length() < size) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      return newInstance(sb.toString());
   }
}
