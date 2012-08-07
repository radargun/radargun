package org.radargun.cachewrappers;

import org.apache.log4j.Logger;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.DefaultConfigurableCacheFactory;
import com.tangosol.net.NamedCache;

/**
 * Oracle Coherence 3.x CacheWrapper implementation.
 * 
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @author <a href="mailto:mlinhard@redhat.com">Michal Linhard</a>
 * @since 1.0.0
 */
public class Coherence3Wrapper implements CacheWrapper {
   private static final String CACHE_NAME = "x"; // this must be synced with cache configs
   private NamedCache nc;
   private Logger log = Logger.getLogger(Coherence3Wrapper.class);

   @Override
   public void setUp(String configuration, boolean isLocal, int nodeIndex, TypedProperties confAttributes)
         throws Exception {
      CacheFactory.setConfigurableCacheFactory(new DefaultConfigurableCacheFactory(configuration));
      nc = CacheFactory.getCache(CACHE_NAME);
      log.debug("CacheFactory.getClusterConfig(): \n" + CacheFactory.getClusterConfig());
      log.debug("CacheFactory.getConfigurableCacheFactoryConfig(): \n"
            + CacheFactory.getConfigurableCacheFactoryConfig());
      log.info("Started Coherence cache " + nc.getCacheName());
   }

   public void tearDown() throws Exception {
      try {
         CacheFactory.destroyCache(nc);
      } catch (IllegalStateException e) {
         if (e.getMessage() != null && e.getMessage().indexOf("Cache is already released") >= 0) {
            log.info("This cache was already destroyed by another instance");
         }
      }
      CacheFactory.shutdown();
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
         return nc.getCacheService().getCluster().getMemberSet().size();
      } catch (IllegalStateException ise) {
         if (ise.getMessage().indexOf("SafeCluster has been explicitly stopped") >= 0) {
            log.info("The cluster is stopped.");
            return 0;
         }
         throw ise;
      }
   }

   public String getInfo() {
      return nc.getCacheName();
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
      return nc.size();
   }
}
