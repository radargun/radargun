package org.radargun.service;

import java.io.InputStream;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.traits.Transactional;

/**
 *
 * An implementation of CacheWrapper that uses Hazelcast instance as an underlying implementation.
 * @author Martin Gencur
 *
 */
@Service(doc = "Hazelcast")
public class HazelcastService implements Lifecycle, Clustered, Transactional.Resource {

   protected final Log log = LogFactory.getLog(getClass());
   private final boolean trace = log.isTraceEnabled();

   protected HazelcastInstance hazelcastInstance;

   @Property(name = "file", doc = "Configuration file.")
   private String config;

   @Property(name = "cache", doc = "Name of the map ~ cache", deprecatedName = "map")
   protected String mapName = "default";

   @ProvidesTrait
   public HazelcastService getSelf() {
      return this;
   }

   @ProvidesTrait
   public Transactional createTransactional() {
      return new Transactional() {
         @Override
         public boolean isTransactional(String cacheName) {
            return true;
         }

         @Override
         public Resource getResource(String cacheName) {
            return HazelcastService.this;
         }
      };
   }

   @ProvidesTrait
   public HazelcastCacheInfo createCacheInfo() {
      return new HazelcastCacheInfo(this);
   }

   @ProvidesTrait
   public HazelcastOperations createOperations() {
      return new HazelcastOperations(this);
   }

   @Override
   public void start() {
      log.info("Creating cache with the following configuration: " + config);
      InputStream configStream = getAsInputStreamFromClassLoader(config);
      Config cfg = new XmlConfigBuilder(configStream).build();
      hazelcastInstance = Hazelcast.newHazelcastInstance(cfg);
      log.info("Hazelcast configuration:" + hazelcastInstance.getConfig().toString());
   }

   @Override
   public void stop() {
      hazelcastInstance.getLifecycleService().shutdown();
   }

   @Override
   public boolean isRunning() {
      return hazelcastInstance.getLifecycleService().isRunning();
   }

   @Override
   public boolean isCoordinator() {
      return false;
   }

   @Override
   public int getClusteredNodes() {
      if (!hazelcastInstance.getLifecycleService().isRunning()) {
         return -1;
      } else {
         return hazelcastInstance.getCluster().getMembers().size();
      }
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
