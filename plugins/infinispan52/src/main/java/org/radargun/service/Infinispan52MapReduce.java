package org.radargun.service;

import java.util.Map;

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
      @SuppressWarnings("unchecked")
      Cache<KIn, VIn> cache = (Cache<KIn, VIn>) service.getCache(null);
      return new MapReduceTask<KIn, VIn, KOut, VOut>(cache, this.distributeReducePhase, this.useIntermediateSharedCache);
   }

   @Override
   public boolean setCombiner(String combinerFqn, Map<String, String> combinerParameters) {
      this.combinerFqn = combinerFqn;
      this.combinerParameters = combinerParameters;
      return true;
   }

   @Override
   protected MapReduceTask<KIn, VIn, KOut, VOut> setCombiner(MapReduceTask<KIn, VIn, KOut, VOut> task) {
      if (this.combinerFqn != null) {
         try {
            Reducer<KOut, VOut> combiner = Utils.instantiate(Thread.currentThread().getContextClassLoader(),
                  this.combinerFqn);
            Utils.invokeMethodWithString(combiner, this.combinerParameters);
            task = task.combinedWith(combiner);
         } catch (Exception e) {
            throw (new IllegalArgumentException("Could not instantiate Combiner class: " + combinerFqn, e));
         }
      }
      return task;
   }

}
