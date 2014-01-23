package org.radargun.stressors;

/**
 * @author Mircea.Markus@jboss.com
 */
public class ObjectKeyGenerator implements KeyGenerator {

   @Override
   public void init(String param, ClassLoader classLoader) {
   }

   @Override
   public Object generateKey(long keyIndex) {
      return new ObjectKey(keyIndex);
   }

}
