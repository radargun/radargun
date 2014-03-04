package org.radargun.service;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.internal.arjuna.objectstore.VolatileStore;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DataLocality;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.Address;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class InfinispanEmbeddedService {
   protected static final String SERVICE_DESCRIPTION = "Service hosting Infinispan in embedded (library) mode.";

   static {
      // Set up transactional stores for JBoss TS
      arjPropertyManager.getCoordinatorEnvironmentBean().setCommunicationStore(VolatileStore.class.getName());
      arjPropertyManager.getObjectStoreEnvironmentBean().setObjectStoreType(VolatileStore.class.getName());
   }

   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanLifecycle lifecycle;

   protected DefaultCacheManager cacheManager;
   protected volatile boolean enlistExtraXAResource;
   protected Map<String, Cache> caches = new HashMap<String, Cache>();

   @Property(name = "file", doc = "File used as a configuration for this service.", deprecatedName = "config")
   protected String configFile;

   @Property(name = "cache", doc = "Name of the main cache. Default is 'testCache'")
   protected String cacheName = "testCache";

   @Property(doc = "Threads per node - for EvenConsistentHash.")
   private int threadsPerNode = -1;

   @Property(doc = "Keys per thread - for EvenConsistentHash.")
   private int keysPerThread = -1;

   public InfinispanEmbeddedService() {
      lifecycle = createLifecycle();
   }

   protected InfinispanLifecycle createLifecycle() {
      return new InfinispanLifecycle(this);
   }

   @ProvidesTrait
   public InfinispanLifecycle getLifecycle() {
      return lifecycle;
   }

   @ProvidesTrait
   public InfinispanOperations createBasicOperations() {
      return new InfinispanOperations(this);
   }

   @ProvidesTrait
   public InfinispanDebugable createDebugable() {
      return new InfinispanDebugable(this);
   }

   @ProvidesTrait
   public InfinispanTransactional createTransactional() {
      return new InfinispanTransactional(this);
   }

   @ProvidesTrait
   public InfinispanCacheInfo createCacheInformation() {
      return new InfinispanCacheInfo(this);
   }

   @ProvidesTrait
   public InfinispanClustered createClustered() {
      return new InfinispanClustered(this);
   }

   protected void startCaches() throws Exception {
      log.trace("Using config file: " + configFile + " and cache name: " + cacheName);

      cacheManager = createCacheManager(configFile);
      String cacheNames = cacheManager.getDefinedCacheNames();
      if (!cacheNames.contains(cacheName))
         throw new IllegalStateException("The requested cache(" + cacheName + ") is not defined. Defined cache " +
                                               "names are " + cacheNames);
      caches.put(null, cacheManager.getCache(cacheName));
      int i = 0;
      for (String name : cacheManager.getCacheNames()) {
         log.trace(i + " adding cache: " + name);
         Cache cache = cacheManager.getCache(name);
         // TODO: remove or rename "buckets", and externalize that
         caches.put("bucket_" + i++, cache);
         caches.put(name, cache);
      }
   }

   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      return new DefaultCacheManager(configFile);
   }

   protected void waitForRehash() throws InterruptedException {
      for (String cacheName : cacheManager.getCacheNames()) {
         Cache cache = cacheManager.getCache(cacheName);
         blockForRehashing(cache);
         injectEvenConsistentHash(cache);
      }
   }

   protected void blockForRehashing(Cache<Object, Object> cache) throws InterruptedException {
      if (isCacheDistributed(cache)) {
         // should we be blocking until all rehashing, etc. has finished?
         long gracePeriod = MINUTES.toMillis(15);
         long giveup = System.currentTimeMillis() + gracePeriod;

         while (!isJoinComplete(cache) && System.currentTimeMillis() < giveup)
            Thread.sleep(200);
         if (!isJoinComplete(cache)) {
            throw new RuntimeException("Caches haven't discovered and joined the cluster even after " + Utils.prettyPrintMillis(gracePeriod));
         }
      }
   }

   protected void injectEvenConsistentHash(Cache<Object, Object> cache) {
      if (isCacheDistributed(cache)) {
         ConsistentHash ch = cache.getAdvancedCache().getDistributionManager().getConsistentHash();
         if (ch instanceof EvenSpreadingConsistentHash) {
            if (threadsPerNode < 0) throw new IllegalStateException("When EvenSpreadingConsistentHash is used threadsPerNode must also be set.");
            if (keysPerThread < 0) throw new IllegalStateException("When EvenSpreadingConsistentHash is used must also be set.");
            ((EvenSpreadingConsistentHash)ch).init(threadsPerNode, keysPerThread);
            log.info("Using an even consistent hash!");
         }
      }
   }

   public Cache<Object, Object> getCache(String cacheName) {
      return caches.get(cacheName);
   }

   /* API that adapts to Infinispan version */

   protected boolean isCacheDistributed(Cache<?, ?> cache) {
      return cache.getConfiguration().getCacheMode().isDistributed();
   }

   protected boolean isCacheClustered(Cache<?, ?> cache) {
      return cache.getConfiguration().getCacheMode().isClustered();
   }

   protected boolean isCacheTransactional(Cache<?, ?> cache) {
      return cache.getAdvancedCache().getTransactionManager() != null;
   }

   protected boolean isCacheAutoCommit(Cache<?, ?> cache) {
      return false;
   }

   protected boolean isJoinComplete(Cache<?, ?> cache) {
      return cache.getAdvancedCache().getDistributionManager().isJoinComplete();
   }

   public int getNumOwners(Cache<?, ?> cache) {
      switch (cache.getConfiguration().getCacheMode()) {
         case LOCAL: return 1;
         case REPL_SYNC:
         case REPL_ASYNC:
            return cacheManager.getMembers().size();
         case INVALIDATION_SYNC:
         case INVALIDATION_ASYNC:
         case DIST_SYNC:
         case DIST_ASYNC:
            return cache.getConfiguration().getNumOwners();
      }
      throw new IllegalStateException();
   }

   protected String getKeyInfo(AdvancedCache cache, Object key) {
      DistributionManager dm = cache.getDistributionManager();
      DataContainer container = cache.getDataContainer();
      StringBuilder sb = new StringBuilder(256);
      sb.append(String.format("Debug info for key %s %s: owners=", cache.getName(), key));
      for (Address owner : dm.locate(key)) {
         sb.append(owner).append(", ");
      }
      DataLocality locality = dm.getLocality(key);
      sb.append("local=").append(locality.isLocal()).append(", uncertain=").append(locality.isUncertain());
      sb.append(", container.").append(key).append('=').append(toString(container.get(key)));
      return sb.toString();
   }

   protected String toString(InternalCacheEntry ice) {
      if (ice == null) return null;
      StringBuilder sb = new StringBuilder(256);
      sb.append(ice.getClass().getSimpleName());
      sb.append("[key=").append(ice.getKey()).append(", value=").append(ice.getValue());
      return sb.append(']').toString();
   }

   protected String getCHInfo(DistributionManager dm) {
      return "\nCH: " + dm.getConsistentHash();
   }
}
