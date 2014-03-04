package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.DefaultConfigurableCacheFactory;
import com.tangosol.net.NamedCache;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;

/**
 * Oracle Coherence 3.x CacheWrapper implementation.
 * 
 * @author Manik Surtani
 * @author <a href="mailto:mlinhard@redhat.com">Michal Linhard</a>
 * @since 1.0.0
 */
// TODO: implement transactions. Would need to rework the xOperations a bit
@Service(doc = "Oracle Coherence 3.x CacheWrapper implementation.")
public class Coherence3Service implements Lifecycle, Clustered {
   private Log log = LogFactory.getLog(Coherence3Service.class);
   
   //private NamedCache nc;
   protected Map<String, NamedCache> caches = new HashMap<String, NamedCache>();

   @Property(name = "file", doc = "Configuration file.", deprecatedName = "config")
   protected String configFile;
   @Property(name = "cache", doc = "Name of the default cache. Default is 'testCache'.")
   protected String cacheName = "testCache";

   @ProvidesTrait
   public Coherence3Service getSelf() {
      return this;
   }

   @ProvidesTrait
   public CoherenceOperations createOperations() {
      return new CoherenceOperations(this);
   }

   @ProvidesTrait
   public CoherenceCacheInfo createCacheInfo() {
      return new CoherenceCacheInfo(this);
   }

   public NamedCache getCache(String name) {
      if (name == null) {
         name = cacheName;
      }
      NamedCache nc = caches.get(name);
      if (nc == null) {
         nc = CacheFactory.getCache(name);
         caches.put(name, nc);
         log.info("Started Coherence cache " + nc.getCacheName());
      }
      return nc;
   }

   @Override
   public synchronized void start() {
      CacheFactory.setConfigurableCacheFactory(new DefaultConfigurableCacheFactory(configFile));
      log.debug("CacheFactory.getClusterConfig(): \n" + CacheFactory.getClusterConfig());
      log.debug("CacheFactory.getConfigurableCacheFactoryConfig(): \n"
            + CacheFactory.getConfigurableCacheFactoryConfig());
      // ensure that at least the main cache is started
      getCache(cacheName);
   }

   private void releaseCache(NamedCache nc) {
      log.info("Relasing cache " + nc.getCacheName());
      try {
         CacheFactory.releaseCache(nc);
      } catch (IllegalStateException e) {
         if (e.getMessage() != null && e.getMessage().indexOf("Cache is already released") >= 0) {
            log.info("This cache was already destroyed by another instance");
         }
      }
   }

   @Override
   public synchronized void stop() {
      for (NamedCache nc : caches.values()) {
         releaseCache(nc);
      }
      caches.clear();
      CacheFactory.shutdown();
      log.info("Cache factory was shut down.");
   }

   @Override
   public synchronized boolean isRunning() {
      return !caches.isEmpty();
   }

   @Override
   public boolean isCoordinator() {
      return false;
   }

   @Override
   public int getClusteredNodes() {
      try {
         return CacheFactory.ensureCluster().getMemberSet().size();
      } catch (IllegalStateException ise) {
         if (ise.getMessage().indexOf("SafeCluster has been explicitly stopped") >= 0) {
            log.info("The cluster is stopped.");
            return 0;
         }
         throw ise;
      }
   }
}
