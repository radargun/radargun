package org.radargun.cachewrappers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.transaction.Status;

import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.rpc.RpcManager;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.radargun.Killable;
import org.radargun.utils.TypedProperties;

/**
 * 
 * InfinispanWrapper that queries all the caches defined in the configuration on round robin basis.
 * 
 * @author Ondrej Nevelik <onevelik@redhat.com>
 */
public class InfinispanMultiCacheWrapper extends InfinispanKillableWrapper implements Killable {

   private Map<Integer, Cache<Object, Object>> caches = null;
   private static Log log = LogFactory.getLog(InfinispanMultiCacheWrapper.class);


   @Override
   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes)
            throws Exception {
      this.config = config;
      String configFile = confAttributes.containsKey("file") ? confAttributes.getProperty("file")
               : config;

      if (!started) {
         cacheManager = new DefaultCacheManager(configFile);

         /*
          * Test case with multiple caches
          */
         String multiCache = (String) confAttributes.get("multiCache");
         if (multiCache == null || multiCache.equals("false")) {
            throw new InstantiationException(
                     "Can't create an instance of InfinispanMultiCacheWrapper! The multiCache conf wasn't set!");
         }
         Set<String> cacheNames = cacheManager.getCacheNames();
         log.trace("Using config file: " + configFile + " and " + cacheNames.size()
                  + " caches defined in it");
         int i = 0;
         caches = new HashMap<Integer, Cache<Object, Object>>(cacheNames.size());
         for (String aCache : cacheNames) {
            log.trace(i + " adding cache: " + aCache);
            caches.put(i++, cacheManager.getCache(aCache));

         }
         /*
          * There SHOULD be just one instance of transaction manager!
          */
         tm = caches.get(0).getAdvancedCache().getTransactionManager();

      }
      log.debug("Loading JGroups from: "
               + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
      log.info("JGroups version: " + org.jgroups.Version.printDescription());
      log.info("Using config attributes: " + confAttributes);

      for (Cache aCache : caches.values()) {
         blockForRehashing(aCache);
         injectEvenConsistentHash(aCache, confAttributes);
      }

      setUpExplicitLocking(caches.get(0), confAttributes);
   }

   /**
    * If transactions are enabled and the locking mode is optimistic then explicit locking is
    * performed! i.e. before any put the key is explicitly locked by cache.lock(key). Doesn't lock
    * the key if the request was made by ClusterValidationStage.
    */
   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      boolean shouldStopTransactionHere = false;
      if (isExplicitLockingEnabled() && !isClusterValidationRequest(bucket)) {
         if (tm.getStatus() == Status.STATUS_NO_TRANSACTION) {
            shouldStopTransactionHere = true;
            startTransaction();
         }
         caches.get(getThreadIdFromBucket(bucket)).getAdvancedCache().lock(key);
      }
      caches.get(getThreadIdFromBucket(bucket)).put(key, value);
      if (shouldStopTransactionHere) {
         endTransaction(true);
      }
   }

   @Override
   public Object get(String bucket, Object key) throws Exception {
      return caches.get(getThreadIdFromBucket(bucket)).get(key);
   }

   @Override
   public void empty() throws Exception {
      for (Cache aCache : caches.values()) {
         empty(aCache);
      }
   }

   private void empty(Cache cache) {
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
   public int size() {
      int size = 0;
      for (Cache aCache : caches.values()) {
         size += aCache.keySet().size();
      }
      return size;
   }

   @Override
   public Cache<Object, Object> getCache() {
      throw new IllegalStateException("Can't get a single cache in multi cache test!");
   }

   @Override
   public void tearDown() throws Exception {
      super.tearDown();
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