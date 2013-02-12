package org.radargun.stressors;

/**
 * @author Mircea.Markus@jboss.com
 */
public class ObjectKeyGenerator implements KeyGenerator {

   @Override
   public Object generateKey(int nodeIndex, int threadIndex, long keyIndex) {
      return new ObjectKey(nodeIndex, threadIndex, keyIndex);
   }

   @Override
   public Object generateKey(int threadIndex, int keyIndex) {
      return new ObjectKey(threadIndex, keyIndex);
   }

   @Override
   public Object generateKey(int keyIndex) {
      return new ObjectKey(-1, keyIndex);
   }

}
