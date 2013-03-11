package org.radargun.cachewrappers;

import java.util.concurrent.ConcurrentHashMap;

import org.radargun.CacheWrapper;
import org.radargun.utils.TypedProperties;

public class ChmWrapper implements CacheWrapper {

   public final ConcurrentHashMap chm = new ConcurrentHashMap();

   @Override
   public void setUp(String configuration, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
   }

   public void tearDown() throws Exception {
   }

   @Override
   public boolean isRunning() {
      return true;
   }

   @Override
   public void put(String bucket, Object key, Object value) throws Exception {
      chm.put(key, value);
   }

   @Override
   public Object get(String bucket, Object key) throws Exception {
      return chm.get(key);
   }
   
   @Override
   public Object remove(String bucket, Object key) throws Exception {
      return chm.remove(key);
   }

   public void empty() throws Exception {
      chm.clear();
   }

   public int getNumMembers() {
      return 1;
   }

   public String getInfo() {
      return "Concurrent hash map wrapper";
   }

   @Override
   public Object getReplicatedData(String bucket, String key) throws Exception {
      return null;
   }

   @Override
   public boolean isTransactional(String bucket) {
      return false;
   }

   public void startTransaction() {
      throw new IllegalStateException("This is not transactional");
   }

   public void endTransaction(boolean successful) {
   }

   @Override
   public int getLocalSize() {
      return chm.size();
   }
   
   @Override
   public int getTotalSize() {
      return chm.size();
   }
}
