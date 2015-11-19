package org.radargun.stages.cache.generators;

import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.utils.Utils;

/**
 * Base for generators that load set of words
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class DictionaryTextObjectGenerator extends TextObjectGenerator {
   @Property(doc = "File with words (one word per line).")
   private String file;

   @Property(doc = "List of words that should be used.")
   private String[] words;

   protected String[] dictionary;

   @Init
   public void initDictionary() {
      if ((file == null && words == null) || (file != null && words != null))
         throw new IllegalArgumentException("Specify either 'file' or 'words'.");
      if (file != null) {
         dictionary = Utils.readFile(file).toArray(new String[0]);
      } else if (words != null) {
         dictionary = words;
      }
   }
}
