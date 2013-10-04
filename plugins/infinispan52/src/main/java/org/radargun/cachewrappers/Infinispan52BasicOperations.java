package org.radargun.cachewrappers;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan52BasicOperations extends Infinispan51BasicOperations {
   protected final Infinispan52Wrapper wrapper;

   public Infinispan52BasicOperations(Infinispan52Wrapper wrapper) {
      super(wrapper);
      this.wrapper = wrapper;
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      super.put(bucket, key, wrapper.wrapValue(value));
   }
}
