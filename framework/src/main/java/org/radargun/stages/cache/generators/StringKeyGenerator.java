package org.radargun.stages.cache.generators;

/**
 * @author Mircea.Markus@jboss.com
 */
public class StringKeyGenerator implements KeyGenerator {
   private String format = "key_%016X";

   @Override
   public void init(String param, ClassLoader classLoader) {
      if (param != null) {
         format = param;
      }
   }

   /* We generate the key indices as zero-padded hex numbers to keep key size with increasing keyIndex */
   @Override
   public Object generateKey(long keyIndex) {
      return String.format(format, keyIndex);
   }
}
