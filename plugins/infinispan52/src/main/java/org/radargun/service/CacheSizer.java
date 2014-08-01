package org.radargun.service;

import java.io.Serializable;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.distexec.DistributedCallable;

/**
 * 
 * A distributed callable class used to get the total size of the cache based on the cache size on
 * each node in the cluster.
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
      return cache.size();
   }

}
