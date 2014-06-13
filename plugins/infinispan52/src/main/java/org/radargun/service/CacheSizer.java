package org.radargun.service;

import java.io.Serializable;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.distexec.DistributedCallable;

/**
 * 
 * A distributed callable class used to get the total size of the cache by counting the primary keys
 * on each node in the cluster.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@SuppressWarnings("serial")
public class CacheSizer<K, V, T> implements DistributedCallable<K, V, Integer>, Serializable {

   Cache<K, V> cache;
   Set<K> keys;

   @Override
   public void setEnvironment(Cache<K, V> cache, Set<K> keys) {
      this.cache = cache;
      this.keys = keys;
   }

   @Override
   public Integer call() throws Exception {
      int primaryKeyCount = 0;
      for (K key : cache.keySet()) {
         if (cache.getAdvancedCache().getDistributionManager().getPrimaryLocation(key)
               .equals(cache.getAdvancedCache().getCacheManager().getAddress())) {
            primaryKeyCount++;
         }
      }
      return primaryKeyCount;
   }

}
