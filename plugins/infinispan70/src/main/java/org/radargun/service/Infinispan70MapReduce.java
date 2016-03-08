package org.radargun.service;

import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.Collator;
import org.infinispan.distexec.mapreduce.MapReduceTask;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class Infinispan70MapReduce<KIn, VIn, KOut, VOut, R> extends Infinispan53MapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan70MapReduce(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   public boolean supportsResultCacheName() {
      return true;
   }

   protected class Builder extends Infinispan53MapReduce<KIn, VIn, KOut, VOut, R>.Builder {
      private String resultCacheName;

      public Builder(Cache<KIn, VIn> cache) {
         super(cache);
      }

      @Override
      public Builder resultCacheName(String resultCacheName) {
         this.resultCacheName = resultCacheName;
         // If this cache is not registered with the service, it can't be accessed by the stage
         service.caches.put(resultCacheName, service.cacheManager.getCache(resultCacheName, true));
         return this;
      }

      @Override
      public Task build() {
         InfinispanMapReduce<KIn, VIn, KOut, VOut, R>.Task task = super.build();
         return new Task(task.mapReduceTask, task.collator, resultCacheName);
      }
   }

   protected class Task extends InfinispanMapReduce<KIn, VIn, KOut, VOut, R>.Task {
      protected final String resultCacheName;

      public Task(MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask, Collator<KOut, VOut, R> collator, String resultCacheName) {
         super(mapReduceTask, collator);
         this.resultCacheName = resultCacheName;
      }

      @Override
      public Map<KOut, VOut> execute() {
         Map<KOut, VOut> result = null;
         if (resultCacheName != null) {
            mapReduceTask.execute(resultCacheName);
         } else {
            result = mapReduceTask.execute();
         }
         return result;
      }
   }

   @Override
   protected Builder builder(Cache<KIn, VIn> cache) {
      return new Builder(cache);
   }
}
