package org.radargun.stages.cache.generators;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@DefinitionElement(name = "string", doc = "Generates strings with configurable format.")
public class StringKeyGenerator implements KeyGenerator {
   @Property(doc = "Formatting string for the keys. Default is 'key_%016X'.")
   private String format = "key_%016X";

   /* We generate the key indices as zero-padded hex numbers to keep key size with increasing keyIndex */
   @Override
   public Object generateKey(long keyIndex) {
      return String.format(format, keyIndex);
   }
}
