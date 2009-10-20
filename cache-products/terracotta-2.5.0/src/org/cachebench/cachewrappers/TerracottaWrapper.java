package org.cachebench.cachewrappers;

import org.cachebench.CacheWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cache wrapper for Terracotta 2.3.0
 * <p/>
 * Using the terracotta-cache package on http://www.terracotta.org/confluence/display/labs/Terracotta+Cache
 *
 * @author <a href="manik@jboss.org">Manik Surtani</a>
 */
public class TerracottaWrapper implements CacheWrapper
{
   // Since terracotta 
   private final Map<List<String>, Map> sessionCaches = new HashMap<List<String>, Map>();

   public void init(Map parameters) throws Exception
   {
   }

   public Object getReplicatedData(List<String> path, String key) throws Exception
   {
      return get(path, key);
   }

   public void setUp() throws Exception
   {
   }

   public void tearDown() throws Exception
   {
      empty();
   }

   public void put(List<String> path, Object key, Object value) throws Exception
   {
      Map cache;
      synchronized (sessionCaches)
      {
         cache = sessionCaches.get(path);
      }
      synchronized (cache)
      {
         cache.put(key, value);
      }
   }

   public Object get(List<String> path, Object key) throws Exception
   {
      Map cache;
      synchronized (sessionCaches)
      {
         cache = sessionCaches.get(path);
      }
      synchronized (cache)
      {
         return cache.get(key);
      }
   }

   public void empty() throws Exception
   {
      synchronized (sessionCaches)
      {
         for (Map cache : sessionCaches.values())
         {
            synchronized (cache)
            {
               cache.clear();
            }
         }
         sessionCaches.clear();
      }
   }

   public int getNumMembers()
   {
      return -1;
   }

   public String getInfo()
   {
      int sz = 0;
      synchronized (sessionCaches)
      {
         for (Map cache : sessionCaches.values())
         {
            synchronized (cache)
            {
               sz += cache.size();
            }
         }
      }

      return "There are " + sz + " objects in cache";
   }

   public Object startTransaction()
   {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful)
   {
      throw new UnsupportedOperationException("Does not support JTA!");
   }
}
