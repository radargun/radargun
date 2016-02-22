package org.radargun.stages.cache.generators;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;

/**
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@DefinitionElement(name = "string", doc = "Generates strings with configurable format.")
public class StringKeyGenerator implements KeyGenerator {
   private static final char[] DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

   @Property(doc = "Formatting string for the keys. Default is 'key_%016X'.")
   private String format; // use "key_%016X" but without the formatter

   /* We generate the key indices as zero-padded hex numbers to keep key size with increasing keyIndex */
   @Override
   public Object generateKey(long keyIndex) {
      if (format != null) {
         return String.format(format, keyIndex);
      } else {
         StringBuilder sb = new StringBuilder(20).append("key_");
         for (int i = 60; i >= 0; i -= 4) {
            sb.append(DIGITS[(int) ((keyIndex >> i) & 15)]);
         }
         return sb.toString();
      }
   }
}
