package org.radargun.cachewrappers;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.internal.arjuna.objectstore.VolatileStore;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.distribution.DataLocality;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.radargun.CacheWrapper;
import org.radargun.features.Debugable;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

public class InfinispanWrapper implements CacheWrapper, Debugable {

   enum State {
      STOPPED,
      STARTING,
      STARTED,
      STOPPING,
      FAILED
   }
   
   static {
      // Set up transactional stores for JBoss TS
      arjPropertyManager.getCoordinatorEnvironmentBean().setCommunicationStore(VolatileStore.class.getName());
      arjPropertyManager.getObjectStoreEnvironmentBean().setObjectStoreType(VolatileStore.class.getName());
   }

   private static final String DEFAULT_CACHE_NAME = "testCache";
   private String cacheName;

   protected final Log log = LogFactory.getLog(getClass());
   private final boolean trace = log.isTraceEnabled();

   protected DefaultCacheManager cacheManager;
   protected TransactionManager tm;
   protected volatile State state = State.STOPPED;
   protected ReentrantLock stateLock = new ReentrantLock();
   protected Thread startingThread;
   protected String config;
   private volatile boolean enlistExtraXAResource;
   private Cache<Object, Object> cache;

   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      this.config = config;
      try {
         if (beginStart()) {
            cacheName = getCacheName(confAttributes);
            setUpCache(confAttributes, nodeIndex);
            setUpTransactionManager();
            
            stateLock.lock();
            state = State.STARTED;
            startingThread = null;
            stateLock.unlock();

            postSetUpInternal(confAttributes);
         }
      } catch (Exception e) {
         log.error("Wrapper start failed.", e);
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         startingThread = null;
         throw e;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }
   
   private void setUpTransactionManager() {
      Cache<?, ?> cache = getCache(null);
      if (cache == null) return;
      tm = cache.getAdvancedCache().getTransactionManager();
      log.info("Using transaction manager: " + tm);
   }

   protected String getConfigFile(TypedProperties confAttributes) {
      // yes, backward compatibility and compatibility with sites
      if (confAttributes.containsKey("config")) return confAttributes.getProperty("config");
      if (confAttributes.containsKey("file")) return confAttributes.getProperty("file");
      return config;
   }
   
   protected String getCacheName(TypedProperties confAttributes) {
      return confAttributes.containsKey("cache") ? confAttributes.getProperty("cache") : DEFAULT_CACHE_NAME;
   }
   
   protected void setUpCache(TypedProperties confAttributes, int nodeIndex) throws Exception {     
      String configFile = getConfigFile(confAttributes);
      String cacheName = getCacheName(confAttributes);
      
      log.trace("Using config file: " + configFile + " and cache name: " + cacheName);

      cacheManager = createCacheManager(configFile);
      String cacheNames = cacheManager.getDefinedCacheNames();
      if (!cacheNames.contains(cacheName))
         throw new IllegalStateException("The requested cache(" + cacheName + ") is not defined. Defined cache " +
                                               "names are " + cacheNames);
      cache = cacheManager.getCache(cacheName);
   }

   protected DefaultCacheManager createCacheManager(String configFile) throws IOException {
      return new DefaultCacheManager(configFile);
   }

   protected void postSetUpInternal(TypedProperties confAttributes) throws Exception {
      log.debug("Loading JGroups from: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
      log.info("JGroups version: " + org.jgroups.Version.printDescription());
      log.info("Using config attributes: " + confAttributes);
      waitForRehash(confAttributes);
   }

   protected void waitForRehash(TypedProperties confAttributes) throws InterruptedException {
      blockForRehashing(getCache(null));
      injectEvenConsistentHash(getCache(null), confAttributes);
   }
   
   public void tearDown() throws Exception {     
      try {
         if (beginStop(false)) {
            List<Address> addressList = cacheManager.getMembers();
            cacheManager.stop();         
            log.info("Stopped, previous view is " + addressList);
                    
            stateLock.lock();
            state = State.STOPPED;
         }
      } catch (Exception e) {
         log.error("Wrapper tear down failed.");
         if (!stateLock.isHeldByCurrentThread()) {
            stateLock.lock();
         }
         state = State.FAILED;
         throw e;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   protected boolean beginStart() throws InterruptedException {
      try {
         stateLock.lock();
         while (state == State.STOPPING) {
            stateLock.unlock();
            log.info("Waiting for the wrapper to stop");
            Thread.sleep(1000);
            stateLock.lock();
         }
         if (state == State.FAILED){
            log.info("Cannot start, previous attempt failed");
         } else if (state == State.STARTING) {
            log.info("Wrapper already starting");
         } else if (state == State.STARTED) {
            log.info("Wrapper already started");
         } else if (state == State.STOPPED) {
            state = State.STARTING;
            startingThread = Thread.currentThread();
            return true;
         }
         return false;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   protected boolean beginStop(boolean interrupt) throws InterruptedException {
      try {
         stateLock.lock();
         if (interrupt && startingThread != null) {
            log.info("Interrupting the starting thread");
            startingThread.interrupt();
         }
         while (state == State.STARTING) {
            stateLock.unlock();
            log.info("Waiting for the wrapper to start");
            Thread.sleep(1000);
            stateLock.lock();
         }
         if (state == State.FAILED) {
            log.info("Cannot stop, previous attempt failed.");
         } else if (state == State.STOPPING) {
            log.warn("Wrapper already stopping");
         } else if (state == State.STOPPED) {
            log.warn("Wrapper already stopped");
         } else if (state == State.STARTED) {
            state = State.STOPPING;
            return true;
         }
         return false;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   @Override
   public boolean isRunning() {
      try {
         stateLock.lock();
         return state == State.STARTED;
      } finally {
         if (stateLock.isHeldByCurrentThread()) {
            stateLock.unlock();
         }
      }
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      if (trace) log.trace("PUT key=" + key);
      getCache(bucket).put(key, value);
   }
   
   @Override
   public Object get(String bucket, Object key) throws Exception {
      if (trace) log.trace("GET key=" + key);
      return getCache(bucket).get(key);
   }
   
   @Override
   public Object remove(String bucket, Object key) throws Exception {
      if (trace) log.trace("REMOVE key=" + key);
      return getCache(bucket).remove(key);
   }

   @Override
   public void empty() throws Exception {
      RpcManager rpcManager = getCache(null).getAdvancedCache().getRpcManager();
      int clusterSize = 0;
      if (rpcManager != null) {
         clusterSize = rpcManager.getTransport().getMembers().size();
      }
      //use keySet().size() rather than size directly as cache.size might not be reliable
      log.info("Cache size before clear (cluster size= " + clusterSize + ")" + getCache(null).keySet().size());

      getCache(null).getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL).clear();
      log.info("Cache size after clear: " + getCache(null).keySet().size());
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

   public Object getReplicatedData(String bucket, String key) throws Exception {
      return get(bucket, key);
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
   public int getLocalSize() {
      return getCache(null).size();
   }
   
   @Override
   public int getTotalSize() {
      return -1; // Infinispan does not provide this directly, JMX stats would have to be summed
   }

   protected void blockForRehashing(Cache<Object, Object> aCache) throws InterruptedException {
      // should we be blocking until all rehashing, etc. has finished?
      long gracePeriod = MINUTES.toMillis(15);
      long giveup = System.currentTimeMillis() + gracePeriod;
      if (aCache.getConfiguration().getCacheMode().isDistributed()) {
         while (!aCache.getAdvancedCache().getDistributionManager().isJoinComplete() && System.currentTimeMillis() < giveup)
            Thread.sleep(200);
      }

      if (aCache.getConfiguration().getCacheMode().isDistributed() && !aCache.getAdvancedCache().getDistributionManager().isJoinComplete())
         throw new RuntimeException("Caches haven't discovered and joined the cluster even after " + Utils.prettyPrintMillis(gracePeriod));
   }

   protected void injectEvenConsistentHash(Cache<Object, Object> aCache, TypedProperties confAttributes) {
      if (aCache.getConfiguration().getCacheMode().isDistributed()) {
         ConsistentHash ch = aCache.getAdvancedCache().getDistributionManager().getConsistentHash();
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

   public Cache<Object, Object> getCache(String bucket) {
      return cache;
   }

   private void assertTm() {
      if (tm == null) throw new RuntimeException("No configured TM!");
   }

   public void setEnlistExtraXAResource(boolean enlistExtraXAResource) {
      this.enlistExtraXAResource = enlistExtraXAResource;
   }
   
   public String getCacheName() {
      return cacheName;
   }

   protected String[] getDebugKeyPackages() {
      return new String[] { "org.infinispan", "org.jgroups" };
   }

   protected String[] getDebugKeyClassesTraceFix() {
      return new String[] { "org.infinispan.container.EntryFactoryImpl" };
   }

   private void setTraceField(ComponentRegistry registry, String clazzName, boolean value) {
      /* Use -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=exclude,my/package/MyClass,myMethod */
      try {
         Class<?> clazz = Class.forName(clazzName);
         Field traceField = clazz.getDeclaredField("trace");
         traceField.setAccessible(true);
         Field modifiers = Field.class.getDeclaredField("modifiers");
         modifiers.setAccessible(true);
         modifiers.setInt(traceField, traceField.getModifiers() & ~Modifier.FINAL);
         if (Modifier.isStatic(traceField.getModifiers())) {
            traceField.setBoolean(null, value);
         } else {
            // if this is instance-variable, try to get instance from registry
            Object component = null;
            component = registry.getComponent(clazz);
            if (component == null) {
               Class<?>[] ifaces = clazz.getInterfaces();
               if (ifaces.length > 0) {
                  component = registry.getComponent(ifaces[0]);
               }
            }
            if (component == null) {
               log.warn("No instance can be found for " + clazzName);
            } else if (!clazz.isAssignableFrom(component.getClass())) {
               log.warn("The actual instance is not " + clazzName + ", it is " + component.getClass().getName());
            } else {
               traceField.setBoolean(component, value);
            }
         }
      } catch (ClassNotFoundException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot load class)", e);
      } catch (NoSuchFieldException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot find field)", e);
      } catch (SecurityException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot access field)", e);
      } catch (IllegalAccessException e) {
         log.warn("Failed to set " + clazzName + "trace=" + value + " (cannot write field)", e);
      } catch (Throwable e) {
         log.warn("Failed to set " + clazzName + "trace=" + value, e);
      }
   }

   @Override
   public void debugKey(String bucket, String key) {
      log.debug(getKeyInfo(bucket, key));
      List<Level> levels = new ArrayList<Level>();
      String[] debugPackages = getDebugKeyPackages();
      ComponentRegistry componentRegistry = getCache(bucket).getAdvancedCache().getComponentRegistry();
      try {
         for (String pkg : debugPackages) {
            Logger logger = Logger.getLogger(pkg);
            levels.add(logger.getLevel());
            logger.setLevel(Level.TRACE);
         }
         for (String clazz : getDebugKeyClassesTraceFix()) {
            setTraceField(componentRegistry, clazz, true);
         }
         getCache(bucket).get(key);
      } finally {
         int i = 0;
         for (Level l : levels) {
            Logger.getLogger(debugPackages[i]).setLevel(l);
            ++i;
         }
         for (String clazz : getDebugKeyClassesTraceFix()) {
            setTraceField(componentRegistry, clazz, false);
         }
      }
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

   @Override
   public void debugInfo(String bucket) {
      DistributionManager dm = getCache(bucket).getAdvancedCache().getDistributionManager();
      DataContainer container = getCache(bucket).getAdvancedCache().getDataContainer();
      StringBuilder sb = new StringBuilder(256);
      sb.append("Debug info for ").append(bucket).append(": joinComplete=").append(dm.isJoinComplete());
      sb.append(", rehashInProgress=").append(dm.isRehashInProgress());
      sb.append(", numEntries=").append(container.size());
      sb.append(getCHInfo(dm));
      log.debug(sb.toString());
   }

   protected String getCHInfo(DistributionManager dm) {
      return "\nCH: " + dm.getConsistentHash();
   }
}
