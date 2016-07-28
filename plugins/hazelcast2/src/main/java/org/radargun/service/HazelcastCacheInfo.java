package org.radargun.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.hazelcast.core.IMap;
import com.hazelcast.core.Instance;
import org.radargun.traits.CacheInformation;

public class HazelcastCacheInfo implements CacheInformation {
   protected final HazelcastService service;

   public HazelcastCacheInfo(HazelcastService service) {
      this.service = service;
   }

   @Override
   public String getDefaultCacheName() {
      return service.mapName;
   }

   @Override
   public Collection<String> getCacheNames() {
      ArrayList<String> names = new ArrayList<String>();
      for (Instance instance : service.hazelcastInstance.getInstances()) {
         if (instance.getInstanceType().isMap()) {
            names.add(((IMap) instance).getName());
         }
      }
      return names;
   }

   @Override
   public Cache getCache(String cacheName) {
      return new Cache(service.getMap(cacheName));
   }

   protected class Cache implements CacheInformation.Cache {
      protected final IMap map;

      public Cache(IMap map) {
         this.map = map;
      }

      @Override
      public long getOwnedSize() {
         return map.getLocalMapStats().getOwnedEntryCount();
      }

      @Override
      public long getLocallyStoredSize() {
         return getMemoryStoredSize();
      }

      @Override
      public long getMemoryStoredSize() {
         return map.getLocalMapStats().getOwnedEntryCount() + map.getLocalMapStats().getBackupEntryCount();
      }

      @Override
      public long getTotalSize() {
         return map.size();
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         return Collections.singletonMap(map.getName(), getOwnedSize());
      }

      @Override
      public int getNumReplicas() {
         return service.hazelcastInstance.getConfig().getMapConfig(map.getName()).getBackupCount() + 1;
      }

      @Override
      public int getEntryOverhead() {
         return -1;
      }
   }
}
