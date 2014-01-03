package org.radargun.cachewrappers;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.features.AtomicOperationsCapable;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanAtomicOperations implements AtomicOperationsCapable {
   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanWrapper wrapper;

   public InfinispanAtomicOperations(InfinispanWrapper wrapper) {
      this.wrapper = wrapper;
   }

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      if (trace) log.trace("REPLACE key=" + key);
      return wrapper.getCache(bucket).replace(key, oldValue, newValue);
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      if (trace) log.trace("PUT_IF_ABSENT key=" + key);
      return wrapper.getCache(bucket).putIfAbsent(key, value);
   }

   @Override
   public boolean remove(String bucket, Object key, Object oldValue) throws Exception {
      if (trace) log.trace("REMOVE_CONDITIONAL key=" + key);
      return wrapper.getCache(bucket).remove(key, oldValue);
   }
}
