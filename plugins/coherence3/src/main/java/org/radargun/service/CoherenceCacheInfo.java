package org.radargun.service;

import java.util.Collection;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.net.PartitionedService;
import com.tangosol.net.management.MBeanHelper;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CoherenceCacheInfo implements CacheInformation {
   private static final Log log = LogFactory.getLog(Coherence3Service.class);
   private static final String CACHE_JMX_NAME_TEMPLATE = "Coherence:type=Cache,service=%s,name=%s,nodeId=%d,tier=back";

   private MBeanServer mBeanServer;
   private String jmxCacheName;
   private final Coherence3Service service;

   public CoherenceCacheInfo(Coherence3Service service) {
      this.service = service;
   }

   @Override
   public String getDefaultCacheName() {
      return service.cacheName;
   }

   @Override
   public Collection<String> getCacheNames() {
      return service.caches.keySet();
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName));
   }

   protected class Cache implements CacheInformation.Cache {
      protected NamedCache nc;

      public Cache(NamedCache cache) {
         nc = cache;
      }

      @Override
      public int getLocalSize() {
         if (nc != null) {
            synchronized (this) {
               if (mBeanServer == null || jmxCacheName == null) {
                  int nodeId = CacheFactory.getCluster().getLocalMember().getId();
                  jmxCacheName = String.format(CACHE_JMX_NAME_TEMPLATE, nc.getCacheService().getInfo().getServiceType(), nc.getCacheName(), nodeId);
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

      @Override
      public int getNumReplicas() {
         if (nc.getCacheService() instanceof PartitionedService) {
            return ((PartitionedService) nc.getCacheService()).getBackupCount() + 1;
         } else {
            return 1;
         }
      }

      @Override
      public int getEntryOverhead() {
         return -1;
      }
   }
}
