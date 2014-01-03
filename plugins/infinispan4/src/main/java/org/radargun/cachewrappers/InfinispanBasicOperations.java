package org.radargun.cachewrappers;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.infinispan.remoting.rpc.RpcManager;
import org.radargun.BasicOperations;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanBasicOperations implements BasicOperations {
   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanWrapper wrapper;

   public InfinispanBasicOperations(InfinispanWrapper wrapper) {
      this.wrapper = wrapper;
   }

   public void put(String bucket, Object key, Object value) throws Exception {
      if (trace) log.trace("PUT key=" + key);
      wrapper.getCache(bucket).put(key, value);
   }

   public Object get(String bucket, Object key) throws Exception {
      if (trace) log.trace("GET key=" + key);
      return wrapper.getCache(bucket).get(key);
   }

   @Override
   public Object getReplicatedData(String bucket, String key) throws Exception {
      throw new UnsupportedOperationException();
   }

   public Object remove(String bucket, Object key) throws Exception {
      if (trace) log.trace("REMOVE key=" + key);
      return wrapper.getCache(bucket).remove(key);
   }

   @Override
   public void clear(boolean local) throws Exception {
      for (String cacheName : wrapper.getCacheManager().getCacheNames()) {
         clear(wrapper.getCacheManager().getCache(cacheName), local);
      }
   }

   protected void clear(Cache<?, ?> cache, boolean local) throws Exception {
      boolean needsTx = wrapper.isCacheTransactional(cache) && !wrapper.isCacheAutoCommit(cache);
      RpcManager rpcManager = cache.getAdvancedCache().getRpcManager();
      int clusterSize = 0;
      if (rpcManager != null) {
         clusterSize = rpcManager.getTransport().getMembers().size();
      }
      log.info("Size of cache " + cache.getName() + " before clear (cluster size= " + clusterSize + ")" + cache.size());

      if (needsTx) {
         cache.getAdvancedCache().getTransactionManager().begin();
      }
      if (local) {
         cache.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).clear();
      } else {
         cache.clear();
      }
      if (needsTx) {
         cache.getAdvancedCache().getTransactionManager().commit();
      }

      log.info("Size of cache " + cache.getName() + " after clear: " + cache.size());
   }
}
