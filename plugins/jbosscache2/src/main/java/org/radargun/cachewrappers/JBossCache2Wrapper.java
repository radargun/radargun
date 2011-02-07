package org.radargun.cachewrappers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.buddyreplication.GravitateResult;
import org.jboss.cache.marshall.NodeData;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

import javax.transaction.Transaction;

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

   public void setUp(String config, boolean ignored, int nodeIndex, TypedProperties confAttributes) throws Exception
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

   public void put(String path, Object key, Object value) throws Exception
   {
      cache.put(Fqn.fromString(path), key, value);
   }

   public Object get(String bucket, Object key) throws Exception
   {
      return cache.get(Fqn.fromString(bucket), key);
   }

   public void empty() throws Exception
   {
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

   @Override
   public int size() {
      return 0;  // TODO: Customise this generated block
   }
}
