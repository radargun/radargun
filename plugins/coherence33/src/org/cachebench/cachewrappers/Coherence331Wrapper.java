package org.cachebench.cachewrappers;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.util.TransactionMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import javax.transaction.Transaction;
import java.util.List;
import java.util.Map;

/**
 * Pass in a -Dtangosol.coherence.localhost=IP_ADDRESS
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @since 2.0.0
 */
public class Coherence331Wrapper implements CacheWrapper
{

   private TransactionMap cache;
   private NamedCache nc;
   boolean localmode;
   Map parameters;
   private Log log = LogFactory.getLog(Coherence331Wrapper.class);

   public void init(Map parameters) throws Exception
   {
      this.parameters = parameters;
   }

   public void setUp() throws Exception
   {
      String configuraton = System.getProperty("cacheBenchFwk.cacheConfigFile");
      String trimmedConfig = configuraton.trim();
      localmode = (Boolean.parseBoolean((String) parameters.get("localOnly")));
      if (trimmedConfig.indexOf("repl") == 0)
      {
         nc = CacheFactory.getCache("repl-CacheBenchmarkFramework");
      }
      else if (trimmedConfig.indexOf("dist") == 0)
      {
         nc = CacheFactory.getCache("dist-CacheBenchmarkFramework");
      }
      else if (trimmedConfig.indexOf("local") == 0)
      {
         nc = CacheFactory.getCache("local-CacheBenchmarkFramework");
      }
      else if (trimmedConfig.indexOf("opt") == 0)
      {
         nc = CacheFactory.getCache("opt-CacheBenchmarkFramework");
      }
      else if (trimmedConfig.indexOf("near") == 0)
      {
         nc = CacheFactory.getCache("near-CacheBenchmarkFramework");
      }
      else
         throw new RuntimeException("Invalid configuration ('" + trimmedConfig + "'). Configuration name should start with: 'dist', 'repl', 'local', 'opt' or 'near'");

      cache = CacheFactory.getLocalTransaction(nc);
      log.info("Starting Coherence cache " + nc.getCacheName());
   }

   public void tearDown() throws Exception
   {
      if (cache != null) nc.release();
   }

   public void put(List<String> path, Object key, Object value) throws Exception
   {
      cache.lock(key);
      try
      {
         cache.put(pathAsString(path, key), value);
      }
      finally
      {
         cache.unlock(key);
      }
   }

   public Object get(List<String> path, Object key) throws Exception
   {
      try
      {
         return cache.get(pathAsString(path, key));
      }
      finally
      {
         cache.unlock(key);
      }
   }

   public void empty() throws Exception
   {
      cache.clear();
   }

   public int getNumMembers()
   {
      return localmode ? 0 : nc.getCacheService().getCluster().getMemberSet().size();
   }

   public String getInfo()
   {
      return nc.getCacheName();
   }

   public Object getReplicatedData(List<String> path, String key) throws Exception
   {
      return get(path, key);
   }

   private String pathAsString(List<String> path, Object key)
   {
      StringBuilder result = new StringBuilder();
      for (String element : path)
      {
         result.append(element);
      }
      result.append(key);
      return result.toString();
   }

   public Transaction startTransaction()
   {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful)
   {
      throw new UnsupportedOperationException("Does not support JTA!");
   }
}
