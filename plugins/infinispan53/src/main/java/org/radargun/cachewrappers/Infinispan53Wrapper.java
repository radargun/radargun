package org.radargun.cachewrappers;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan53Wrapper extends Infinispan52Wrapper {
   @Override
   protected Infinispan53MapReduce createMapReduce() {
      return new Infinispan53MapReduce(this);
   }

   @Override
   public int getValueByteOverhead() {
      return 136;
   }
}
