package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCache;
import org.radargun.traits.BulkOperations;

import java.util.Map;
import java.util.Set;

/**
 * @author Matej Cimbora
 */
public class Infinispan72HotRodOperations extends HotRodOperations {

   public Infinispan72HotRodOperations(InfinispanHotrodService service) {
      super(service);
   }

   @Override
   public <K, V> BulkOperations.Cache<K, V> getCache(String cacheName, boolean preferAsync) {
      if (cacheName == null) {
         cacheName = service.getCacheName();
      }
      if (cacheName == null) {
         return new Infinispan72HotRodBulkOperationsCache<>((RemoteCache<K,V>) service.getManagerNoReturn().getCache(false),
                                                            (RemoteCache<K,V>) service.getManagerForceReturn().getCache(true), preferAsync);
      } else {
         return new Infinispan72HotRodBulkOperationsCache<>((RemoteCache<K,V>) service.getManagerNoReturn().getCache(cacheName, false),
                                                            (RemoteCache<K,V>) service.getManagerForceReturn().getCache(cacheName, true), preferAsync);
      }
   }

   protected class Infinispan72HotRodBulkOperationsCache<K, V> extends HotRodOperations.HotRodBulkOperationsCache<K, V> {

      public Infinispan72HotRodBulkOperationsCache(RemoteCache<K, V> noReturn, RemoteCache<K, V> forceReturn, boolean preferAsync) {
         super(noReturn, forceReturn, preferAsync);
      }

      @Override
      public Map<K, V> getAll(Set<K> keys) {
         if (preferAsync) {
            return super.getAll(keys);
         } else {
            if (trace) log.tracef("GET_ALL cache=%s keys=%s", forceReturn.getName(), keys);
            return forceReturn.getAll(keys);
         }
      }
   }
}
