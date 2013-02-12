package org.radargun.stressors;

/**
 * @author Mircea.Markus@jboss.com
 */
public class StringKeyGenerator implements KeyGenerator {

   /* We generate the key indices as zero-padded hex numbers to keep key size with increasing keyIndex */

   @Override
   public Object generateKey(int nodeIndex, int threadIndex, long keyIndex) {
      return String.format("key_%d_%d_%016X", nodeIndex, threadIndex, keyIndex);
   }

   @Override
   public Object generateKey(int threadIndex, int keyIndex) {
      return String.format("key_%d_%016X", threadIndex, keyIndex);
   }

   @Override
   public Object generateKey(int keyIndex) {
      return String.format("key_%016X", keyIndex);
   }
}
