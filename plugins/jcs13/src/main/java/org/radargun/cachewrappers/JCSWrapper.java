package org.radargun.cachewrappers;

import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

import org.apache.jcs.JCS;

/**
 * // TODO: Document this
 *
 * @author Mark Tun
 * @author Galder Zamarreño
 * @since // TODO
 */
public class JCSWrapper implements CacheWrapper {

   private JCS cache;

   @Override
   public void setUp(String config, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      JCS.setConfigFilename("/local_cache.ccf");
      cache = JCS.getInstance("default");
   }

   public void tearDown() throws Exception {
      empty();
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      cache.put(key, value);
   }


   @Override
   public Object get(String bucket, Object key) throws Exception {
      return cache.get(key);
   }

   @Override
   public Object getReplicatedData(String bucket, String key) throws Exception {
      return get(bucket, key);
   }

   public void empty() throws Exception {
      cache.clear();
   }

   public int getNumMembers() {
      return -1;
   }

   public String getInfo() {
      int sz = 0;

      return "There are " + sz + " objects in cache";
   }

   public void startTransaction() {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   public void endTransaction(boolean successful) {
      throw new UnsupportedOperationException("Does not support JTA!");
   }

   @Override
   public int size() {
      return 0;
   }
}
