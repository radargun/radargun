package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.Property;
import org.radargun.utils.Utils;

/**
 * Generates sentence made up from words in the dictionary.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SentenceGenerator extends TextObjectGenerator {
   @Property(doc = "File with words (one word per line).", optional = false)
   private String file;

   private String[] dictionary;

   @Override
   public void init(String param, ClassLoader classLoader) {
      super.init(param, classLoader);
      dictionary = Utils.readFile(file).toArray(new String[0]);
   }

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
