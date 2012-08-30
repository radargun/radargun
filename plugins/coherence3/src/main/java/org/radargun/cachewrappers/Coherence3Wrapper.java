package org.radargun.cachewrappers;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.log4j.Logger;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.DefaultConfigurableCacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.net.management.MBeanHelper;

/**
 * Oracle Coherence 3.x CacheWrapper implementation.
 * 
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @author <a href="mailto:mlinhard@redhat.com">Michal Linhard</a>
 * @since 1.0.0
 */
public class Coherence3Wrapper implements CacheWrapper {
   private static final String PROP_SERVICE = "service";
   private static final String PROP_CACHE = "cache";
   private static final String DEFAULT_CACHE_NAME = "x";
   private static final String DEFAULT_SERVICE_NAME = "DistributedCache";
   private static final String CACHE_JMX_NAME_TEMPLATE = "Coherence:type=Cache,service=%s,name=%s,nodeId=%d,tier=back";
   
   private Logger log = Logger.getLogger(Coherence3Wrapper.class);
   
   private NamedCache nc;
   private MBeanServer mBeanServer;
   private String cacheName;
   private String serviceName;
   private String jmxCacheName;
   
   @Override
   public void setUp(String configuration, boolean isLocal, int nodeIndex, TypedProperties confAttributes)
         throws Exception {
      cacheName = confAttributes.containsKey(PROP_CACHE) ? confAttributes.getProperty(PROP_CACHE) : DEFAULT_CACHE_NAME;
      serviceName = confAttributes.containsKey(PROP_SERVICE) ? confAttributes.getProperty(PROP_SERVICE) : DEFAULT_SERVICE_NAME;
      CacheFactory.setConfigurableCacheFactory(new DefaultConfigurableCacheFactory(configuration));
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
   public void put(String bucket, Object key, Object value) throws Exception {
      nc.put(key, value);
   }

   @Override
   public Object get(String bucket, Object key) throws Exception {
      return nc.get(key);
   }

   public void empty() throws Exception {
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

   public void startTransaction() {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful) {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   @Override
   public int size() {
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
}
