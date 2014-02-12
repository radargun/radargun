package org.radargun.cachewrappers;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.DefaultConfigurableCacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.net.management.MBeanHelper;
import org.radargun.CacheWrapper;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Oracle Coherence 3.x CacheWrapper implementation.
 * 
 * @author Manik Surtani
 * @author <a href="mailto:mlinhard@redhat.com">Michal Linhard</a>
 * @since 1.0.0
 */
public class Coherence3Wrapper implements CacheWrapper {
   private static final String CACHE_JMX_NAME_TEMPLATE = "Coherence:type=Cache,service=%s,name=%s,nodeId=%d,tier=back";
   
   private Log log = LogFactory.getLog(Coherence3Wrapper.class);
   
   private NamedCache nc;
   private MBeanServer mBeanServer;
   private String jmxCacheName;

   @Property(name = "file", doc = "Configuration file.", deprecatedName = "config")
   private String configFile;
   @Property(name = "cache", doc = "Name of the default cache. Default is 'testCache'.")
   private String cacheName = "testCache";
   @Property(name = "service", doc = "Name of the default service. Default is 'DistributedCache")
   private String serviceName = "DistributedCache";
   
   @Override
   public void setUp(boolean isLocal, int nodeIndex)
         throws Exception {
      CacheFactory.setConfigurableCacheFactory(new DefaultConfigurableCacheFactory(configFile));
      nc = CacheFactory.getCache(cacheName);
      log.debug("CacheFactory.getClusterConfig(): \n" + CacheFactory.getClusterConfig());
      log.debug("CacheFactory.getConfigurableCacheFactoryConfig(): \n"
            + CacheFactory.getConfigurableCacheFactoryConfig());
      log.info("Started Coherence cache " + nc.getCacheName());
   }

   public void tearDown() throws Exception {
      log.info("Relasing cache " + nc.getCacheName());
      try {
         CacheFactory.releaseCache(nc);
         nc = null;
      } catch (IllegalStateException e) {
         if (e.getMessage() != null && e.getMessage().indexOf("Cache is already released") >= 0) {
            log.info("This cache was already destroyed by another instance");
         }
      }
      CacheFactory.shutdown();
      log.info("Cache factory was shut down.");
   }

   @Override
   public boolean isRunning() {
      return nc.isActive();
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      nc.put(key, value);
   }

   @Override
   public Object get(String bucket, Object key) throws Exception {
      return nc.get(key);
   }
   
   @Override
   public Object remove(String bucket, Object key) throws Exception {
      return nc.remove(key);
   }

   @Override
   public void clear(boolean local) throws Exception {
      if (local) {
         log.warn("This cache cannot remove only local entries");
      }
      nc.clear();
   }

   public int getNumMembers() {
      try {
         if (nc == null) {
            return 0;
         } else {
            return nc.getCacheService().getCluster().getMemberSet().size();
         }
      } catch (IllegalStateException ise) {
         if (ise.getMessage().indexOf("SafeCluster has been explicitly stopped") >= 0) {
            log.info("The cluster is stopped.");
            return 0;
         }
         throw ise;
      }
   }

   public String getInfo() {
      if (nc != null) {
         return nc.getCacheName();
      } else {
         log.info("Cache is not available.");
         return null;
      }
   }

   @Override
   public Object getReplicatedData(String bucket, String key) throws Exception {
      return get(bucket, key);
   }

   @Override
   public boolean isTransactional(String bucket) {
      return false;
   }

   public void startTransaction() {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful) {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   @Override
   public int getLocalSize() {
      if (nc != null) {
         synchronized (this) {
            if (mBeanServer == null || jmxCacheName == null) {
               int nodeId = CacheFactory.getCluster().getLocalMember().getId();
               jmxCacheName = String.format(CACHE_JMX_NAME_TEMPLATE, serviceName, cacheName, nodeId);
               mBeanServer = MBeanHelper.findMBeanServer();
            }
         }
         try {
            AttributeList list = mBeanServer.getAttributes(new ObjectName(jmxCacheName), new String[] { "Units", "UnitFactor" } );
            return ((Integer) ((Attribute) list.get(0)).getValue()) * ((Integer) ((Attribute)list.get(1)).getValue()); 
         } catch (Exception e) {
            log.warn("Failed to retrieve JMX info from object " + jmxCacheName + "\n" + e);
            return -1;
         }         
      } else {
         log.info("Cache is not available.");
         return -1;
      }
   }
   
   @Override
   public int getTotalSize() {
      return nc == null ? -1 : nc.size(); 
   }
}
