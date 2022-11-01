package org.radargun.service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.coordinator.TransactionReaper;
import com.arjuna.ats.arjuna.coordinator.TxControl;
import com.arjuna.ats.internal.arjuna.objectstore.VolatileStore;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DataLocality;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.transport.Address;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

import static java.util.concurrent.TimeUnit.MINUTES;

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
   protected final InfinispanClustered clustered;

   protected volatile DefaultCacheManager cacheManager;
   protected volatile boolean enlistExtraXAResource;
   protected Map<String, Cache> caches = new HashMap<String, Cache>();

   @Property(name = Service.FILE, doc = "File used as a configuration for this service.", deprecatedName = "config")
   protected String configFile;

   @Property(name = "cache", doc = "Name of the main cache. Default is 'testCache'")
   protected String cacheName = "testCache";

   @Property(doc = "Threads per node - for EvenConsistentHash.")
   private int threadsPerNode = -1;

   @Property(doc = "Keys per thread - for EvenConsistentHash.")
   private int keysPerThread = -1;

   public InfinispanEmbeddedService() {
      lifecycle = createLifecycle();
      clustered = createClustered();
   }

   protected InfinispanLifecycle createLifecycle() {
      return new InfinispanLifecycle(this);
   }

   protected InfinispanClustered createClustered() {
      return new InfinispanClustered(this);
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
   public InfinispanClustered getClustered() {
      return clustered;
   }

   protected void startCaches() throws Exception {
      log.trace("Using config file: " + configFile + " and cache name: " + cacheName);

      TxControl.enable();
      TransactionReaper.instantiate();
      log.tracef("TxControl: %s", TxControl.isEnabled() ? "enabled" : "disabled");

      cacheManager = createCacheManager(configFile);
      cacheManager.start();
      try {
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
      } catch (Exception e) {
         log.trace("Failed to start caches", e);
         try {
            cacheManager.stop();
         } catch (Exception se) {
            log.error("Failed to stop after start failed", se);
         }
         throw e;
      }
   }

   protected void stopCaches() {
      try {
         cacheManager.stop();
      } finally {
         caches.clear();
         forcedCleanup();
         clustered.stopped();
      }
   }

   protected void forcedCleanup() {
      try {
         TxControl.disable(true);
      } catch (Exception e) {
         log.error("Failed to stop transaction manager", e);
      }
      try {
         TransactionReaper.terminate(true);
      } catch (Exception e) {
         log.error("Failed to stop transaction reaper", e);
      }
      String jmxDomain = getJmxDomain();
      MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
      try {
         for (ObjectName objectName : mbeanServer.queryNames(new ObjectName(jmxDomain + ":*"), null)) {
            try {
               mbeanServer.unregisterMBean(objectName);
            } catch (Exception e) {
               log.warn("Cannot unregister MBean " + objectName, e);
            }
         }
      } catch (MalformedObjectNameException e) {
         log.error("Failed to unregister MBeans", e);
      }
   }

   protected String getJmxDomain() {
      return cacheManager.getGlobalConfiguration().getJmxDomain();
   }

   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      DefaultCacheManager cm = new DefaultCacheManager(configFile, false);
      beforeCacheManagerStart(cm);
      return cm;
   }

   protected void beforeCacheManagerStart(DefaultCacheManager cacheManager) {
      cacheManager.addListener(getClustered());
   }

   protected void waitForRehash() throws InterruptedException {
      for (String cacheName : cacheManager.getCacheNames()) {
         Cache cache = cacheManager.getCache(cacheName);
         blockForRehashing(cache);
      }
   }

   protected void blockForRehashing(Cache<Object, Object> cache) throws InterruptedException {
      if (isCacheDistributed(cache)) {
         // should we be blocking until all rehashing, etc. has finished?
         long gracePeriod = MINUTES.toMillis(15);
         long giveup = TimeService.currentTimeMillis() + gracePeriod;

         while (!isJoinComplete(cache) && TimeService.currentTimeMillis() < giveup)
            Thread.sleep(200);
         if (!isJoinComplete(cache)) {
            throw new RuntimeException("Caches haven't discovered and joined the cluster even after " + Utils.prettyPrintMillis(gracePeriod));
         }
      }
   }

   public Cache<Object, Object> getCache(String cacheName) {
      return caches.get(cacheName);
   }

   public EmbeddedCacheManager getCacheManager() {
      return cacheManager;
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
         case LOCAL:
            return 1;
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
      sb.append("Debug info for key ").append(cache.getName()).append(' ').append(key).append(": owners=");
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
