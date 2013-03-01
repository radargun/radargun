package org.radargun.cachewrappers;

import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.internal.arjuna.objectstore.VolatileStore;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

/**
 * // TODO: Document this
 *
 * @author Mark Tun
 * @author Galder Zamarreño
 * @since // TODO
 */
public class HotRodWrapper implements CacheWrapper {

   private static Log log = LogFactory.getLog(HotRodWrapper.class);
   private RemoteCacheManager cacheManager;
   private RemoteCache<Object, Object> cache;
   boolean started = false;
   String config;

   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      log.trace("Test Server mode ");

      if (!started) {
         cacheManager = new RemoteCacheManager();
         cacheManager.start();
         cache = cacheManager.getCache();
         started = true;
      }
      log.info("Using config attributes: " + confAttributes);
   }

   public void tearDown() throws Exception {
      if (started) {
         cacheManager.stop();
         started = false;
      }
   }

   public void put(String bucket, Object key, Object value) throws Exception {
      cache.put(key, value);
   }

   public Object get(String bucket, Object key) throws Exception {
      return cache.get(key);
   }

   public void empty() throws Exception {
      //use keySet().size() rather than size directly as cache.size might not be reliable
      //log.info("Cache size before clear " + cache.keySet().size());

      cache.clear();
      //log.info("Cache size after clear: " + cache.keySet().size());
   }

   public int getNumMembers() {
      return 0;
   }

   public String getInfo() {
      return "Running : " + cache.getVersion() +  ", config:" + config + ", cacheName:" + cache.getName();
   }

   public Object getReplicatedData(String bucket, String key) throws Exception {
      return get(bucket, key);
   }


   @Override
   public int size() {
      return cache.keySet().size();
   }

   public void startTransaction() {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful) {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

}
