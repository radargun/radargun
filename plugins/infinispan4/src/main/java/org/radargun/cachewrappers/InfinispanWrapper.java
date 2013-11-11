package org.radargun.cachewrappers;

import static java.util.concurrent.TimeUnit.MINUTES;

import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.internal.arjuna.objectstore.VolatileStore;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.infinispan.Cache;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DataLocality;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.Address;
import org.radargun.CacheWrapper;
import org.radargun.features.AtomicOperationsCapable;
import org.radargun.features.Debugable;
import org.radargun.features.ProvidesMemoryOverhead;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

public class InfinispanWrapper implements CacheWrapper, Debugable, AtomicOperationsCapable, ProvidesMemoryOverhead {

   static {
      // Set up transactional stores for JBoss TS
      arjPropertyManager.getCoordinatorEnvironmentBean().setCommunicationStore(VolatileStore.class.getName());
      arjPropertyManager.getObjectStoreEnvironmentBean().setObjectStoreType(VolatileStore.class.getName());
   }

   private static final String DEFAULT_CACHE_NAME = "testCache";
   private String cacheName;

   protected final Log log = LogFactory.getLog(getClass());
   protected final boolean trace = log.isTraceEnabled();

   protected final InfinispanLifecycle lifecycle;
   protected final InfinispanBasicOperations basicOperations;
   protected final InfinispanAtomicOperations atomicOperations;
   protected final InfinispanDebugable debugable;

   protected DefaultCacheManager cacheManager;
   protected TransactionManager tm;
   protected String config;
   protected TypedProperties confAttributes;
   protected int nodeIndex;
   protected volatile boolean enlistExtraXAResource;
   protected Map<String, Cache> buckets = new HashMap<String, Cache>();

   public InfinispanWrapper() {
      lifecycle = createLifecycle();
      basicOperations = createBasicOperations();
      atomicOperations = createAtomicOperations();
      debugable = createDebugable();
   }

   protected InfinispanLifecycle createLifecycle() {
      return new InfinispanLifecycle(this);
   }

   protected InfinispanBasicOperations createBasicOperations() {
      return new InfinispanBasicOperations(this);
   }

   protected InfinispanAtomicOperations createAtomicOperations() {
      return new InfinispanAtomicOperations(this);
   }

   protected InfinispanDebugable createDebugable() {
      return new InfinispanDebugable(this);
   }

   protected void setUpTransactionManager() {
      Cache<?, ?> cache = getCache(null);
      if (cache == null) return;
      tm = cache.getAdvancedCache().getTransactionManager();
      log.info("Using transaction manager: " + tm);
   }

   protected String getConfigFile() {
      // yes, backward compatibility and compatibility with sites
      if (confAttributes.containsKey("config")) return confAttributes.getProperty("config");
      if (confAttributes.containsKey("file")) return confAttributes.getProperty("file");
      return config;
   }

   protected void setUpCaches() throws Exception {
      String mainCacheName = getMainCacheName();
      String configFile = getConfigFile();

      log.trace("Using config file: " + configFile + " and cache name: " + mainCacheName);

      cacheManager = createCacheManager(configFile);
      String cacheNames = cacheManager.getDefinedCacheNames();
      if (!cacheNames.contains(mainCacheName))
         throw new IllegalStateException("The requested cache(" + mainCacheName + ") is not defined. Defined cache " +
                                               "names are " + cacheNames);
      buckets.put(null, cacheManager.getCache(mainCacheName));
      int i = 0;
      for (String name : cacheManager.getCacheNames()) {
         log.trace(i + " adding cache: " + name);
         Cache cache = cacheManager.getCache(name);
         buckets.put("bucket_" + i++, cache);
         buckets.put(name, cache);
      }
   }

   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      return new DefaultCacheManager(configFile);
   }

   public DefaultCacheManager getCacheManager() {
      return cacheManager;
   }

   protected void waitForRehash() throws InterruptedException {
      for (String cacheName : cacheManager.getCacheNames()) {
         Cache cache = cacheManager.getCache(cacheName);
         blockForRehashing(cache);
         injectEvenConsistentHash(cache);
      }
   }

   protected void blockForRehashing(Cache<Object, Object> cache) throws InterruptedException {
      // should we be blocking until all rehashing, etc. has finished?
      long gracePeriod = MINUTES.toMillis(15);
      long giveup = System.currentTimeMillis() + gracePeriod;
      if (isCacheDistributed(cache)) {
         while (!cache.getAdvancedCache().getDistributionManager().isJoinComplete() && System.currentTimeMillis() < giveup)
            Thread.sleep(200);
      }

      if (isCacheDistributed(cache) && !cache.getAdvancedCache().getDistributionManager().isJoinComplete())
         throw new RuntimeException("Caches haven't discovered and joined the cluster even after " + Utils.prettyPrintMillis(gracePeriod));
   }

   protected void injectEvenConsistentHash(Cache<Object, Object> cache) {
      if (isCacheDistributed(cache)) {
         ConsistentHash ch = cache.getAdvancedCache().getDistributionManager().getConsistentHash();
         if (ch instanceof EvenSpreadingConsistentHash) {
            int threadsPerNode = confAttributes.getIntProperty("threadsPerNode", -1);
            if (threadsPerNode < 0) throw new IllegalStateException("When EvenSpreadingConsistentHash is used threadsPerNode must also be set.");
            int keysPerThread = confAttributes.getIntProperty("keysPerThread", -1);
            if (keysPerThread < 0) throw new IllegalStateException("When EvenSpreadingConsistentHash is used must also be set.");
            ((EvenSpreadingConsistentHash)ch).init(threadsPerNode, keysPerThread);
            log.info("Using an even consistent hash!");
         }
      }
   }

   @Override
   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      log.debug("Loading JGroups from: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
      log.info("JGroups version: " + org.jgroups.Version.printDescription());
      log.info("Using config attributes: " + confAttributes);
      this.config = config;
      this.nodeIndex = nodeIndex;
      this.confAttributes = confAttributes;
      lifecycle.setUp();
   }

   @Override
   public void tearDown() throws Exception {
      lifecycle.tearDown();
   }

   @Override
   public boolean isRunning() {
      return lifecycle.isRunning();
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      basicOperations.put(bucket, key, value);
   }
   
   @Override
   public Object get(String bucket, Object key) throws Exception {
      return basicOperations.get(bucket, key);
   }

   public Object getReplicatedData(String bucket, String key) throws Exception {
      return basicOperations.get(bucket, key);
   }
   
   @Override
   public Object remove(String bucket, Object key) throws Exception {
      return basicOperations.remove(bucket, key);
   }

   @Override
   public void clear(boolean local) throws Exception {
      basicOperations.clear(local);
   }

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      return atomicOperations.replace(bucket, key, oldValue, newValue);
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      return atomicOperations.putIfAbsent(bucket, key, value);
   }

   @Override
   public boolean remove(String bucket, Object key, Object oldValue) throws Exception {
      return atomicOperations.remove(bucket, key, oldValue);
   }

   @Override
   public void debugKey(String bucket, Object key) {
      debugable.debugKey(bucket, key);
   }

   @Override
   public void debugInfo(String bucket) {
      debugable.debugInfo(bucket);
   }

   public int getNumMembers() {
      ComponentRegistry componentRegistry = getCache(null).getAdvancedCache().getComponentRegistry();
      if (componentRegistry.getStatus().startingUp()) {
         log.trace("We're in the process of starting up.");
      }
      if (cacheManager.getMembers() != null) {
         log.trace("Members are: " + cacheManager.getMembers());
      }
      return cacheManager.getMembers() == null ? 0 : cacheManager.getMembers().size();
   }

   public String getInfo() {
      //Important: don't change this string without validating the ./dist.sh as it relies on its format!!
      return "Running : " + getCache(null).getVersion() +  ", config:" + config + ", cacheName:" + getCache(null).getName();
   }

   public void startTransaction() {
      assertTm();
      try {
         tm.begin();
         Transaction transaction = tm.getTransaction();
         if (enlistExtraXAResource) {
            transaction.enlistResource(new DummyXAResource());
         }
      }
      catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public void endTransaction(boolean successful) {
      assertTm();
      try {
         if (successful)
            tm.commit();
         else
            tm.rollback();
      }
      catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public boolean isTransactional(String bucket) {
      return tm != null;
   }

   private void assertTm() {
      if (tm == null) throw new RuntimeException("No configured TM!");
   }

   public void setEnlistExtraXAResource(boolean enlistExtraXAResource) {
      this.enlistExtraXAResource = enlistExtraXAResource;
   }

   public int getTransactionStatus() throws SystemException {
      return tm.getStatus();
   }

   @Override
   public int getLocalSize() {
      int sum = 0;
      for (String cacheName : cacheManager.getCacheNames()) {
         // main cache is in the map twice
         sum += cacheManager.getCache(cacheName).size();
      }
      return sum;
   }
   
   @Override
   public int getTotalSize() {
      return -1; // Infinispan does not provide this directly, JMX stats would have to be summed
   }

   public Cache<Object, Object> getCache(String bucket) {
      return buckets.get(bucket);
   }

   public Set<String> getBuckets() {
      return buckets.keySet();
   }

   public String getMainCacheName() {
      if (cacheName == null) {
         cacheName = confAttributes.containsKey("cache") ? confAttributes.getProperty("cache") : DEFAULT_CACHE_NAME;
      }
      return cacheName;
   }

   @Override
   public int getValueByteOverhead() {
      return -1;
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

   protected String getKeyInfo(String bucket, Object key) {
      DistributionManager dm = getCache(bucket).getAdvancedCache().getDistributionManager();
      DataContainer container = getCache(bucket).getAdvancedCache().getDataContainer();
      StringBuilder sb = new StringBuilder(256);
      sb.append(String.format("Debug info for key %s %s: owners=", bucket, key));
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
