package org.radargun.service;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.utils.Utils;

public class Infinispan52MapReduce<KIn, VIn, KOut, VOut, R> extends InfinispanMapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan52MapReduce(Infinispan52EmbeddedService service) {
      super(service);
   }

   @Override
   public boolean setDistributeReducePhase(boolean distributeReducePhase) {
      this.distributeReducePhase = distributeReducePhase;
      return true;
   }

   @Override
   public boolean setUseIntermediateSharedCache(boolean useIntermediateSharedCache) {
      this.useIntermediateSharedCache = useIntermediateSharedCache;
      return true;
   }

   @Override
   protected MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTaskFactory() {
      Cache<KIn, VIn> cache = (Cache<KIn, VIn>) service.getCache(null);
      return new MapReduceTask<KIn, VIn, KOut, VOut>(cache, this.distributeReducePhase, this.useIntermediateSharedCache);
   }

   @Override
   public boolean setCombiner(String combinerFqn) {
      this.combinerFqn = combinerFqn;
      return true;
   }

   @Override
   protected MapReduceTask<KIn, VIn, KOut, VOut> setCombiner(MapReduceTask<KIn, VIn, KOut, VOut> task,
                                                             String combinerFqn) {
      if (combinerFqn != null) {
         try {
            @SuppressWarnings("unchecked")
            Reducer<KOut, VOut> combiner = Utils.instantiate(Thread.currentThread().getContextClassLoader(), combinerFqn);
            Utils.invokeMethodWithString(combiner, this.combinerParameters);
            task = task.combinedWith(combiner);
         } catch (Exception e) {
            throw (new IllegalArgumentException("Could not instantiate Combiner class: " + combinerFqn, e));
         }
      }
      return task;
   }

}
