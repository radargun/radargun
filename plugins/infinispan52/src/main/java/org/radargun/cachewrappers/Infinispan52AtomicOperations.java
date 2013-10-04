package org.radargun.cachewrappers;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan52AtomicOperations extends Infinispan51AtomicOperations {
   protected final Infinispan52Wrapper wrapper;

   public Infinispan52AtomicOperations(Infinispan52Wrapper wrapper) {
      super(wrapper);
      this.wrapper = wrapper;
   }

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      return super.replace(bucket, key, wrapper.wrapValue(oldValue), wrapper.wrapValue(newValue));
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      return super.putIfAbsent(bucket, key, wrapper.wrapValue(value));
   }

   @Override
   public boolean remove(String bucket, Object key, Object oldValue) throws Exception {
      return super.remove(bucket, key, wrapper.wrapValue(oldValue));
   }
}
