package org.radargun.cachewrappers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.radargun.CacheWrapper;
import org.radargun.features.AtomicOperationsCapable;
import org.radargun.features.BulkOperationsCapable;
import org.radargun.utils.TypedProperties;

public class ChmWrapper implements CacheWrapper, BulkOperationsCapable, AtomicOperationsCapable {

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

   @Override
   public boolean replace(String bucket, Object key, Object oldValue, Object newValue) throws Exception {
      return chm.replace(key, oldValue, newValue);
   }

   @Override
   public Object putIfAbsent(String bucket, Object key, Object value) throws Exception {
      return chm.putIfAbsent(key, value);
   }

   @Override
   public boolean remove(String bucket, Object key, Object oldValue) throws Exception {
      return chm.remove(key, oldValue);
   }

   @Override
   public Collection<Object> getAll(String bucket, Collection<Object> keys) throws Exception {
      List<Object> values = new ArrayList<Object>(keys.size());
      for (Object key : keys) {
         values.add(chm.get(key));
      }
      return values;
   }

   @Override
   public void putAll(String bucket, Map<Object, Object> entries) throws Exception {
      chm.putAll(entries);
   }

   @Override
   public Collection<Object> removeAll(String bucket, Collection<Object> keys) throws Exception {
      List<Object> values = new ArrayList<Object>(keys.size());
      for (Object key : keys) {
         values.add(chm.remove(key));
      }
      return values;
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
