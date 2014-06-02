package org.radargun.service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.infinispan.AdvancedCache;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedExecutorService;
import org.infinispan.distexec.DistributedTaskBuilder;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan52CacheInfo extends InfinispanCacheInfo {
   private final Log log = LogFactory.getLog(this.getClass());

   public Infinispan52CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected class Cache extends InfinispanCacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public int getEntryOverhead() {
         return 152;
      }
      
      @Override
      public int getTotalSize() {
         int totalSize = 0;
         DistributedExecutorService des = new DefaultExecutorService(cache);
         DistributedTaskBuilder<Integer> taskBuilder = des.createDistributedTaskBuilder(new CacheSizer());
         List<Future<Integer>> futureList = des.submitEverywhere(taskBuilder.build());
         for (Future<Integer> future : futureList) {
            try {
               totalSize += future.get().intValue();
            } catch (InterruptedException e) {
               log.error("The distributed task was interrupted.", e);
               return -1;
            } catch (ExecutionException e) {
               log.error("An error occurred executing the distributed task.", e);
               return -1;
            }
         }
         return totalSize;         
      }
   }
}
