package org.radargun.query;

import java.util.Random;

import org.radargun.utils.Utils;

/**
 * Just picks single word from the dictionary, ignoring size constraints.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SingleWordGenerator extends TextObjectGenerator {
   private String[] dictionary;

   @Override
   public void init(String param, ClassLoader classLoader) {
      dictionary = Utils.readFile(param).toArray(new String[0]);
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      return new TextObject(dictionary[random.nextInt(dictionary.length)]);
   }

   @Override
   public boolean checkValue(Object value, int expectedSize) {
      return ((TextObject) value).text != null;
   }
}
