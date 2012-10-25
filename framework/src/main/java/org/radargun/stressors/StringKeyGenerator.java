package org.radargun.stressors;

/**
 * @author Mircea.Markus@jboss.com
 */
public class StringKeyGenerator implements KeyGenerator {

   @Override
   public Object generateKey(int nodeIndex, int threadIndex, int keyIndex) {
      return "key_" + nodeIndex + "_" + threadIndex + "_" + keyIndex;
   }

   @Override
   public Object generateKey(int threadIndex, int keyIndex) {
      return "key_" + threadIndex + "_" + keyIndex;
   }

   @Override
   public Object generateKey(int keyIndex) {
      return "key_" + keyIndex;
   }
}
