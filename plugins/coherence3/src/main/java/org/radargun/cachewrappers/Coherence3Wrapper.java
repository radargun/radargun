package org.radargun.cachewrappers;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.CacheWrapper;

import javax.transaction.Transaction;

/**
 * Pass in a -Dtangosol.coherence.localhost=IP_ADDRESS
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @since 2.0.0
 */
public class Coherence3Wrapper implements CacheWrapper {

   private NamedCache nc;
   private Log log = LogFactory.getLog(Coherence3Wrapper.class);

   @Override
   public void setUp(String configuration, boolean isLocal, int nodeIndex) throws Exception {
      String config;
      if (configuration.indexOf("repl") == 0) {
         config = "radargun-repl";
      } else if (configuration.indexOf("dist") == 0) {
         config = "radargun-dist";
      } else if (configuration.indexOf("near") == 0) {
         config = "radargun-near";
      } else {
         throw new RuntimeException("Invalid configuration ('" + configuration + "'). Configuration name should start with: 'dist', 'repl', 'local', 'opt' or 'near'");
      }
      nc = CacheFactory.getCache(config);

      log.info("Starting Coherence cache " + nc.getCacheName());
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

   public Transaction startTransaction() {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful) {
      throw new UnsupportedOperationException("Does not support JTA!");
   }
}
