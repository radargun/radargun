package org.radargun.cachewrappers;

import java.io.InputStream;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.features.AtomicOperationsCapable;
import org.radargun.utils.TypedProperties;

/**
 *
 * An implementation of CacheWrapper that uses Hazelcast instance as an underlying implementation.
 * @author Martin Gencur
 *
 */
public class HazelcastWrapper implements CacheWrapper, AtomicOperationsCapable {

   protected final Log log = LogFactory.getLog(getClass());
   private final boolean trace = log.isTraceEnabled();

   private static final String DEFAULT_MAP_NAME = "default";
   protected HazelcastInstance hazelcastInstance;
   protected IMap<Object, Object> hazelcastMap;

   @Override
   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      log.info("Creating cache with the following configuration: " + config);
      String mapName = getMapName(confAttributes);
      InputStream configStream = getAsInputStreamFromClassLoader(config);
      Config cfg = new XmlConfigBuilder(configStream).build();
      hazelcastInstance = Hazelcast.newHazelcastInstance(cfg);
      log.info("Hazelcast configuration:" + hazelcastInstance.getConfig().toString());
      hazelcastMap = hazelcastInstance.getMap(mapName);
   }

   protected String getMapName(TypedProperties confAttributes) {
      return confAttributes.containsKey("map") ? confAttributes.getProperty("map") : DEFAULT_MAP_NAME;
   }

   @Override
   public void tearDown() throws Exception {
      hazelcastInstance.getLifecycleService().shutdown();
   }

   @Override
   public boolean isRunning() {
      return hazelcastInstance.getLifecycleService().isRunning();
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      if (trace) log.trace("PUT key=" + key);
      hazelcastMap.put(key, value);
   }

   @Override
   public Object get(String bucket, Object key) throws Exception {
      if (trace) log.trace("GET key=" + key);
      return hazelcastMap.get(key);
   }

   @Override
   public Object remove(String bucket, Object key) throws Exception {
      if (trace) log.trace("REMOVE key=" + key);
      return hazelcastMap.remove(key);
   }

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      return hazelcastMap.replace(key, oldValue, newValue);
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      return hazelcastMap.putIfAbsent(key, value);
   }

   @Override
   public boolean remove(String bucket, Object key, Object oldValue) throws Exception {
      return hazelcastMap.remove(key, oldValue);
   }

   @Override
   public void clear(boolean local) throws Exception {
      if (local) {
         log.warn("This cache cannot remove only local entries");
      }
      hazelcastMap.clear();
   }

   @Override
   public int getNumMembers() {
      if (trace) log.trace("Cluster size=" + hazelcastInstance.getCluster().getMembers().size());
      if (!hazelcastInstance.getLifecycleService().isRunning())
         return -1;
      else
         return hazelcastInstance.getCluster().getMembers().size();
   }

   @Override
   public String getInfo() {
      return "There are " + hazelcastMap.size() + " entries in the cache.";
   }

   @Override
   public Object getReplicatedData(String bucket, String key) throws Exception {
      return get(bucket, key);
   }

   @Override
   public boolean isTransactional(String bucket) {
      return true;
   }

   @Override
   public void startTransaction() {
      try {
         hazelcastInstance.getTransaction().begin();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void endTransaction(boolean successful) {
      try {
         if (successful) {
            hazelcastInstance.getTransaction().commit();
         } else {
            hazelcastInstance.getTransaction().rollback();
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public int getLocalSize() {
      return -1; //not supported by Hazelcast, local size can be monitored through Hazelcast management center (web GUI)
   }

   @Override
   public int getTotalSize() {
      return hazelcastMap.size();
   }

   private InputStream getAsInputStreamFromClassLoader(String filename) {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      InputStream is;
      try {
         is = cl == null ? null : cl.getResourceAsStream(filename);
      } catch (RuntimeException re) {
         // could be valid; see ISPN-827
         is = null;
      }
      if (is == null) {
         try {
            // check system class loader
            is = getClass().getClassLoader().getResourceAsStream(filename);
         } catch (RuntimeException re) {
            // could be valid; see ISPN-827
            is = null;
         }
      }
      return is;
   }
}
