package org.radargun.cachewrappers;

import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.jboss.cache.*;
import org.jboss.cache.buddyreplication.GravitateResult;
import org.jboss.cache.marshall.NodeData;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.radargun.CacheWrapper;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@SuppressWarnings("unchecked")
public class JBossCache2Wrapper implements CacheWrapper
{
   private Cache cache;
   private Log log = LogFactory.getLog(JBossCache2Wrapper.class);
   private boolean inLocalMode;

   @Property(name = "file", doc = "Configuration file.")
   private String config;

   public void setUp(boolean ignored, int nodeIndex) throws Exception
   {
      log.info("Creating cache with the following configuration: " + config);
      cache = new DefaultCacheFactory().createCache(config);
      log.info("Running cache with following config: " + cache.getConfiguration());
      log.info("Running follwing JBossCacheVersion: " + org.jboss.cache.Version.version);
      log.info("Running follwing JBossCacheCodeName: " + org.jboss.cache.Version.codename);
//      inLocalMode = config.containsKey("localOnly");       TODO fix this
   }

   public void tearDown() throws Exception
   {
      cache.stop();
   }

   @Override
   public boolean isRunning() {
      return cache.getCacheStatus() == CacheStatus.STARTED;
   }

   public void put(String path, Object key, Object value) throws Exception
   {
      cache.put(Fqn.fromString(path), key, value);
   }

   public Object get(String bucket, Object key) throws Exception
   {
      return cache.get(Fqn.fromString(bucket), key);
   }
   
   public Object remove(String bucket, Object key) throws Exception
   {
      return cache.remove(Fqn.fromString(bucket), key);
   }

   public void clear(boolean local) throws Exception
   {
      if (local) {
         log.warn("This cache cannot remove only local entries");
      }
      //not removing root because there it fails with buddy replication: http://jira.jboss.com/jira/browse/JBCACHE-1241
      cache.removeNode(Fqn.fromElements("test"));
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
      CacheSPI cacheSpi = (CacheSPI) cache;
      GravitateResult result = cacheSpi.gravitateData(Fqn.fromString(path), true, new InvocationContext());
      if (!result.isDataFound())
      {
         //totall replication?
         return get(path, key);
      }
      NodeData nodeData = result.getNodeData().get(0);
      return nodeData.getAttributes().get(key);
   }

   @Override
   public boolean isTransactional(String bucket) {
      return true;
   }

   public void startTransaction()
   {
      try
      {
         DummyTransactionManager.getInstance().begin();
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

   @Override
   public int getLocalSize() {
      return -1;  // TODO: Customise this generated block
   }
   
   @Override
   public int getTotalSize() {
      return -1;  // TODO: Customise this generated block
   }
}
