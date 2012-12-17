package org.radargun.cachewrappers;

import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache wrapper for Terracotta 2.3.0
 * <p/>
 * Using the terracotta-cache package on http://www.terracotta.org
 *
 * @author Manik Surtani
 * @author Mircea.Markus@jboss.com
 */
public class TerracottaWrapper implements CacheWrapper {

   private final Map<String, Map> sessionCaches = new HashMap<String, Map>();

   @Override
   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
   }

   public void tearDown() throws Exception {
      empty();
   }

   @Override
   public boolean isRunning() {
      return true;
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      Map sessionMap = getSessionMap(bucket);
      if (sessionMap == null) {
         sessionMap = createSessionMap(bucket);
      }
      synchronized (sessionMap) {
         sessionMap.put(key, value);
      }
   }


   @Override
   public Object get(String bucket, Object key) throws Exception {
      Map sessionMap = getSessionMap(bucket);
      if (sessionMap == null) return null;
      synchronized (sessionMap) {
         return sessionMap.get(key);
      }
   }
   
   @Override
   public Object remove(String bucket, Object key) throws Exception {
      Map sessionMap = getSessionMap(bucket);
      if (sessionMap == null) return null;
      synchronized (sessionMap) {
         return sessionMap.remove(key);
      }
   }

   @Override
   public Object getReplicatedData(String bucket, String key) throws Exception {
      return get(bucket, key);
   }

   public void empty() throws Exception {
      synchronized (sessionCaches) {
         for (Map cache : sessionCaches.values()) {
            synchronized (cache) {
               cache.clear();
            }
         }
         sessionCaches.clear();
      }
   }

   public int getNumMembers() {
      return -1;
   }

   public String getInfo() {
      int sz = 0;
      synchronized (sessionCaches) {
         for (Map cache : sessionCaches.values()) {
            synchronized (cache) {
               sz += cache.size();
            }
         }
      }

      return "There are " + sz + " objects in cache";
   }

   public void startTransaction() {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful) {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   @Override
   public int getLocalSize() {
      return -1;  // TODO: Customise this generated block
   }
   
   @Override
   public int getTotalSize() {
      return -1;  // TODO: Customise this generated block
   }

   /**
    * This will be read-synchronized by default (tc-client-config.xml).
    */
   public Map getSessionMap(String sessionId) {
      synchronized (sessionCaches) {
         return sessionCaches.get(sessionId);
      }
   }

   /**
    * This will be write synchronized by default (tc-client-config.xml).
    */
   public Map createSessionMap(String bucket) {
      synchronized (sessionCaches) {
         Map result = new HashMap();
         sessionCaches.put(bucket, result);
         return sessionCaches.get(bucket);
      }
   }
}
