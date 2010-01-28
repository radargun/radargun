package org.cachebench.cachewrappers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.jboss.cache.Cache;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Option;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.Caches;

import javax.transaction.Transaction;
import java.util.Map;

/**
 * @author Mircea.Markus@jboss.com
 */
@SuppressWarnings("unchecked")
public class JBossCache3Wrapper implements CacheWrapper
{
   private Cache cache;
   private Map flatCache;
   private Log log = LogFactory.getLog(JBossCache3Wrapper.class);
   private final boolean FLAT; // this  is final so that the compiler inlines it and it doesn't become a reason for a perf bottleneck

   public JBossCache3Wrapper()
   {
      FLAT = Boolean.getBoolean("cacheBenchFwk.useFlatCache");
   }

   public void setUp(String config, boolean ignored) throws Exception
   {
      log.info("Creating cache with the following configuration: " + config);
      cache = new DefaultCacheFactory().createCache(config);
      log.info("Running cache with following config: " + cache.getConfiguration());
      log.info("Running following JBossCacheVersion: " + org.jboss.cache.Version.version);
      log.info("Running following JBossCacheCodeName: " + org.jboss.cache.Version.codename);
      log.info("JGroups version: " + org.jgroups.Version.description);
      if (FLAT)
      {
         log.info("Using FLAT MAP wrapper");
         flatCache = Caches.asMap(cache);
      }
   }

   public void tearDown() throws Exception
   {
      cache.stop();
   }

   public void put(String path, Object key, Object value) throws Exception
   {
      if (FLAT)
         flatCache.put(key, value);
      else
         cache.put(path, key, value);
   }

   public Object get(String bucket, Object key) throws Exception
   {
      if (FLAT)
         return flatCache.get(key);
      else
         return cache.get(Fqn.fromString(bucket), key);
   }

   public void empty() throws Exception
   {
      if (FLAT)
      {
         flatCache.clear();
      }
      else
      {
         //not removing root because there it fails with buddy replication: http://jira.jboss.com/jira/browse/JBCACHE-1241
         cache.removeNode(Fqn.fromElements("test"));
      }
   }

   public int getNumMembers()
   {
      return cache.getMembers() == null ? 0 : cache.getMembers().size();
   }

   public String getInfo()
   {
      return "Num direct children: " + cache.getRoot().getChildren().size();
   }

   public Object getReplicatedData(String path, String key) throws Exception
   {
      if (!cache.getConfiguration().getCacheMode().isSynchronous())
      {
         log.info("Sleeping 5 seconds because the cache is replicated asynchronious!");
         Thread.sleep(5000);
      }
      Option option = cache.getInvocationContext().getOptionOverrides();
      option.setForceDataGravitation(true);
      return get(path, key);
   }


   public Transaction startTransaction()
   {
      try
      {
         DummyTransactionManager.getInstance().begin();
         return DummyTransactionManager.getInstance().getTransaction();
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   public void endTransaction(boolean successful)
   {
      try
      {
         if (successful)
            DummyTransactionManager.getInstance().commit();
         else
            DummyTransactionManager.getInstance().rollback();
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }
}
