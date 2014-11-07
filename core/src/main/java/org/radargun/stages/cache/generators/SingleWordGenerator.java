package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.utils.Utils;

/**
 * Just picks single word from the dictionary, ignoring size constraints.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SingleWordGenerator extends TextObjectGenerator {
   @Property(doc = "File with words (one word per line).", optional = false)
   private String file;

   private String[] dictionary;

   @Init
   public void initDictionary() {
      dictionary = Utils.readFile(file).toArray(new String[0]);
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      return newInstance(dictionary[random.nextInt(dictionary.length)]);
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      return getText(value) != null;
   }
}
