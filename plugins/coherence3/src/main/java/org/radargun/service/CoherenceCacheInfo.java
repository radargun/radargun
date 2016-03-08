package org.radargun.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.tangosol.coherence.component.util.SafeNamedCache;
import com.tangosol.coherence.component.util.daemon.queueProcessor.service.grid.ReplicatedCache;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.CacheService;
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
         nc = cache instanceof SafeNamedCache ? ((SafeNamedCache) cache).getNamedCache() : cache;
      }

      @Override
      public long getOwnedSize() {
         if (nc.getCacheService() instanceof PartitionedService) {
            return getCacheSize();
         } else {
            return -1;
         }
      }

      /**
       * The size reports owned entries for distributed (partitioned) cache and all entries for replicated cache
       * @return
       */
      protected long getCacheSize() {
         if (nc != null) {
            synchronized (this) {
               if (mBeanServer == null || jmxCacheName == null) {
                  int nodeId = CacheFactory.getCluster().getLocalMember().getId();
                  jmxCacheName = String.format(CACHE_JMX_NAME_TEMPLATE, nc.getCacheService().getInfo().getServiceType(), nc.getCacheName(), nodeId);
                  mBeanServer = MBeanHelper.findMBeanServer();
               }
            }
            try {
               AttributeList list = mBeanServer.getAttributes(new ObjectName(jmxCacheName), new String[] {"Units", "UnitFactor"});
               return ((Integer) ((Attribute) list.get(0)).getValue()) * ((Integer) ((Attribute) list.get(1)).getValue());
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
      public long getLocallyStoredSize() {
         if (nc.getCacheService() instanceof PartitionedService) {
            return -1;
         } else {
            return getCacheSize();
         }
      }

      @Override
      public long getMemoryStoredSize() {
         return -1;
      }

      @Override
      public long getTotalSize() {
         return nc == null ? -1 : nc.size();
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         return Collections.singletonMap(nc.getCacheName(), getOwnedSize());
      }

      @Override
      public int getNumReplicas() {
         CacheService service = nc.getCacheService();
         if (service instanceof PartitionedService) {
            return ((PartitionedService) service).getBackupCount() + 1;
         } else if (service instanceof ReplicatedCache) {
            return service.getCluster().getMemberSet().size();
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
