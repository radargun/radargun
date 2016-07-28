package org.radargun.service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.ConditionalOperations;

public class ChmCache implements BasicOperations.Cache, ConditionalOperations.Cache, CacheInformation.Cache {
   private final ConcurrentHashMap chm = new ConcurrentHashMap();
   private final String name;

   public ChmCache(String name) {
      this.name = name;
   }

   @Override
   public Object get(Object key) {
      return chm.get(key);
   }

   @Override
   public boolean containsKey(Object key) {
      return chm.containsKey(key);
   }

   @Override
   public void put(Object key, Object value) {
      chm.put(key, value);
   }

   @Override
   public Object getAndPut(Object key, Object value) {
      return chm.put(key, value);
   }

   @Override
   public boolean putIfAbsent(Object key, Object value) {
      return chm.putIfAbsent(key, value) == null;
   }

   @Override
   public boolean remove(Object key, Object oldValue) {
      return chm.remove(key, oldValue);
   }

   @Override
   public boolean remove(Object key) {
      return chm.remove(key) != null;
   }

   @Override
   public Object getAndRemove(Object key) {
      return chm.remove(key);
   }

   @Override
   public boolean replace(Object key, Object value) {
      for (; ; ) {
         Object oldValue = chm.get(key);
         if (oldValue == null) return false;
         if (chm.replace(key, oldValue, value)) return true;
      }
   }

   @Override
   public boolean replace(Object key, Object oldValue, Object newValue) {
      return chm.replace(key, oldValue, newValue);
   }

   @Override
   public Object getAndReplace(Object key, Object value) {
      for (; ; ) {
         Object oldValue = chm.get(key);
         if (oldValue == null) return null;
         if (chm.replace(key, oldValue, value)) return oldValue;
      }
   }

   @Override
   public void clear() {
      chm.clear();
   }

   @Override
   public long getOwnedSize() {
      return chm.size();
   }

   @Override
   public long getLocallyStoredSize() {
      return chm.size();
   }

   @Override
   public long getMemoryStoredSize() {
      return chm.size();
   }

   @Override
   public long getTotalSize() {
      return chm.size();
   }

   @Override
   public Map<?, Long> getStructuredSize() {
      return Collections.singletonMap(name, (long) chm.size());
   }

   @Override
   public int getNumReplicas() {
      return 1;
   }

   @Override
   public int getEntryOverhead() {
      return -1;
   }
}
