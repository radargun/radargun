package org.radargun.cachewrappers;

import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.rpc.RpcManager;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.radargun.utils.TypedProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 
 * InfinispanWrapper that queries all the caches defined in the configuration on round robin basis.
 * 
 * @author Ondrej Nevelik <onevelik@redhat.com>
 */
public class InfinispanMultiCacheWrapper extends InfinispanKillableWrapper {

   private Map<Integer, Cache<Object, Object>> caches = null;
   private static Log log = LogFactory.getLog(InfinispanMultiCacheWrapper.class);
  
   @Override
   protected void setUpCache(TypedProperties confAttributes, int nodeIndex) throws Exception {
      String configFile = getConfigFile(confAttributes);
      cacheManager = new DefaultCacheManager(configFile);

      Set<String> cacheNames = cacheManager.getCacheNames();
      log.trace("Using config file: " + configFile + " and " + cacheNames.size()
               + " caches defined in it");
      int i = 0;
      caches = new HashMap<Integer, Cache<Object, Object>>(cacheNames.size());
      for (String aCache : cacheNames) {
         log.trace(i + " adding cache: " + aCache);
         caches.put(i++, cacheManager.getCache(aCache));

      }
   }
     
   @Override
   protected void waitForRehash(TypedProperties confAttributes) throws InterruptedException {
      for (Cache<Object, Object> cache : caches.values()) {
         blockForRehashing(cache);
         injectEvenConsistentHash(cache, confAttributes);
      }
   }

   @Override
   public void empty() throws Exception {
      for (Cache<Object, Object> aCache : caches.values()) {
         empty(aCache);
      }
   }

   private void empty(Cache<Object, Object> cache) {
      RpcManager rpcManager = cache.getAdvancedCache().getRpcManager();
      int clusterSize = 0;
      if (rpcManager != null) {
         clusterSize = rpcManager.getTransport().getMembers().size();
      }
      // use keySet().size() rather than size directly as cache.size might not
      // be reliable
      log.info("Size of cache " + cache.getName() + " before clear (cluster size= " + clusterSize
               + ")" + cache.keySet().size());

      cache.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).clear();
      log.info("Size of cache " + cache.getName() + " after clear: " + cache.keySet().size());
   }

   /**
    * multiCache test breaks the contract of super.getInfo() method used by bin/dist.sh script!
    */
   @Override
   public String getInfo() {
      return "Running multi cache test!";

   }

   @Override
   public int getLocalSize() {
      int size = 0;
      for (Cache<Object, Object> aCache : caches.values()) {
         size += aCache.keySet().size();
      }
      return size;
   }
   
   @Override
   public int getTotalSize() {
      int size = 0;
      for (Cache<Object, Object> aCache : caches.values()) {
         size += aCache.size();
      }
      return size;
   }

   @Override
   public Cache<Object, Object> getCache(String bucket) {
      if (bucket == null) return caches.get(0);
      return caches.get(getThreadIdFromBucket(bucket));
   }

   @Override
   public int getNumMembers() {
      ComponentRegistry componentRegistry = caches.get(0).getAdvancedCache().getComponentRegistry();
      if (componentRegistry.getStatus().startingUp()) {
         log.trace("We're in the process of starting up.");
      }
      if (cacheManager.getMembers() != null) {
         log.trace("Members are: " + cacheManager.getMembers());
      }
      return cacheManager.getMembers() == null ? 0 : cacheManager.getMembers().size();
   }

   /**
    * Following the contract in PutGetSterssor that a bucket for distributed benchmark is in the
    * form of: nodeIndex + "_" + threadIndex;
    * 
    * @param bucket
    *           string to parse the threadIndex from
    * @return
    */
   private int getThreadIdFromBucket(String bucket) {
      if (!bucket.contains("_")) {
         return 0;
      }
      String[] parts = bucket.split("_");
      if (parts.length != 2) {
         throw new IllegalArgumentException(
                  "There should be two parts when parsing thread id from bucket string: " + bucket);
      }
      return Integer.parseInt(parts[1]);
   }

}