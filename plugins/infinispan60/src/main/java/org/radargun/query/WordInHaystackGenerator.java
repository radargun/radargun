package org.radargun.query;

import java.util.Random;

import org.radargun.utils.Utils;

/**
 * Surrounds the word with nonsense. Use for wildcard queries.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class WordInHaystackGenerator extends TextObjectGenerator {
   private static char[] ALPHABET = "abcdefghijklmnopqrstuvw 1234567890".toCharArray();

   private String[] dictionary;

   @Override
   public void init(String param, ClassLoader classLoader) {
      dictionary = Utils.readFile(param).toArray(new String[0]);
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      String word = dictionary[random.nextInt(dictionary.length)];
      int position = random.nextInt(size - word.length());
      StringBuilder sb = new StringBuilder(size);
      for (int i = position; i > 0; --i) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      sb.append(word);
      while (sb.length() < size) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      return new TextObject(sb.toString());
   }
}
