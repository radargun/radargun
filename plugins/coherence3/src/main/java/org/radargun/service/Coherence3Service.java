package org.radargun.service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.ConfigurableCacheFactory;
import com.tangosol.net.DefaultConfigurableCacheFactory;
import com.tangosol.net.NamedCache;
import org.radargun.Service;
import org.radargun.config.Converter;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;

/**
 * Oracle Coherence 3.x CacheWrapper implementation.
 * 
 * @author Manik Surtani &lt;msurtani@gmail.com&gt;
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
@Service(doc = "Oracle Coherence 3.x CacheWrapper implementation.")
public class Coherence3Service implements Lifecycle, Clustered {
   private Log log = LogFactory.getLog(Coherence3Service.class);

   protected Map<String, NamedCache> caches = new HashMap<String, NamedCache>();
   protected boolean started = false;

   @Property(name = "file", doc = "Configuration file.", deprecatedName = "config")
   protected String configFile;
   @Property(name = "cache", doc = "Name of the default cache. Default is 'testCache'.")
   protected String cacheName = "testCache";

   @Property(doc = "Attributes that should be indexed, in form cache:attribute,cache:attribute. By default, nothing is indexed.",
      converter = IndexedColumnsConverter.class)
   protected List<IndexedColumn> indexedColumns = Collections.EMPTY_LIST;

   @Property(doc = "Used to lookup the connection factory from InitialContext. By default DefaultConnectionFactory is used.")
   protected String connectionFactory;

   @Property(doc = "Service used when retrieving the connection. Default is the default service ('TransactionalCache').")
   protected String transactionalService;

   @Property(doc = "Use POF (Portable Object Format) for serialization instead of Java serialization. Default is true.")
   protected boolean usePOF = true;

   protected CoherenceQueryable queryable = new CoherenceQueryable(this);

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

   @ProvidesTrait
   public CoherenceQueryable getQueryable() {
      return queryable;
   }

//   @ProvidesTrait
//   public CoherenceTransactional createTransactional() {
//      return new CoherenceTransactional(this);
//   }

   public NamedCache getCache(String name) {
      assertStarted();
      if (name == null) {
         name = cacheName;
      }
      NamedCache nc = caches.get(name);
      if (nc == null) {
         nc = CacheFactory.getCache(name);
         if (nc == null) {
            throw new IllegalArgumentException("Cache " + name + " cannot be retrieved.");
         }
         caches.put(name, nc);
         queryable.registerIndices(nc, indexedColumns);
         log.info("Started Coherence cache " + nc.getCacheName());
      }
      return nc;
   }

   protected ConfigurableCacheFactory createCacheFactory() {
      return new DefaultConfigurableCacheFactory(configFile);
   }

   @Override
   public synchronized void start() {
      if (usePOF) {
         System.setProperty("tangosol.pof.enabled", "true");
         System.setProperty("tangosol.pof.config", "pof-config.xml");
      } else {
         System.setProperty("tangosol.pof.enabled", "false");
      }
      System.setProperty("tangosol.coherence.cacheconfig", configFile);
//      CacheFactory.setConfigurableCacheFactory(createCacheFactory());
      started = true;
      // ensure that at least the main cache is started
      getCache(cacheName);
      log.info("Started Coherence Service");
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
      started = false;
      log.info("Cache factory was shut down.");
   }

   @Override
   public synchronized boolean isRunning() {
      return started;
   }

   @Override
   public boolean isCoordinator() {
      return false;
   }

   @Override
   public int getClusteredNodes() {
      assertStarted();
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

   protected void assertStarted() {
      if (!started) throw new IllegalStateException("Cache is not started");
   }

   protected static class IndexedColumn {
      public final String cache;
      public final String attribute;
      public final boolean ordered;

      public IndexedColumn(String cache, String attribute, boolean ordered) {
         this.cache = cache;
         this.attribute = attribute;
         this.ordered = ordered;
      }

      @Override
      public String toString() {
         return String.format("[cache=%s, attribute=%s, sorted=%s]", cache, attribute, ordered);
      }
   }

   private static class IndexedColumnsConverter implements Converter<List<IndexedColumn>> {
      @Override
      public List<IndexedColumn> convert(String string, Type type) {
         String[] parts = string.split("(,|\\n)");
         ArrayList<IndexedColumn> list = new ArrayList<IndexedColumn>(parts.length);
         for (String part : parts) {
            String[] ca = part.split(":");
            if (ca.length == 2) {
               list.add(new IndexedColumn(ca[0].trim(), ca[1].trim(), false));
            } else if (ca.length == 3) {
               list.add(new IndexedColumn(ca[0].trim(), ca[1].trim(), ca[2].trim().equalsIgnoreCase("ordered")));
            } else {
               throw new IllegalArgumentException(string);
            }
         }
         return list;
      }

      @Override
      public String convertToString(List<IndexedColumn> value) {
         StringBuilder sb = new StringBuilder();
         for (IndexedColumn c : value) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.toString());
         }
         return sb.toString();
      }

      @Override
      public String allowedPattern(Type type) {
         return ".*:.*(:ordered)?((,|\\n).*:.*(:ordered)?)*";
      }
   }
}
