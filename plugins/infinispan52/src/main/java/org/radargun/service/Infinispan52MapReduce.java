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

   protected class Builder extends InfinispanMapReduce<KIn, VIn, KOut, VOut, R>.Builder {
      protected boolean distributedReducePhase;
      protected boolean useIntermediateSharedCache;
      protected Reducer<KOut, VOut> combiner;

      public Builder(Cache<KIn, VIn> cache) {
         super(cache);
      }

      @Override
      public Builder distributedReducePhase(boolean distributedReducePhase) {
         this.distributedReducePhase = distributedReducePhase;
         return this;
      }

      @Override
      public Builder useIntermediateSharedCache(boolean useIntermediateSharedCache) {
         this.useIntermediateSharedCache = useIntermediateSharedCache;
         return this;
      }

      @Override
      public Builder combiner(String combinerFqn, Map<String, String> combinerParameters) {
         try {
            combiner = Utils.instantiate(combinerFqn);
            Utils.invokeMethodWithString(combiner, combinerParameters);
         } catch (Exception e) {
            throw (new IllegalArgumentException("Could not instantiate Combiner class: " + combinerFqn, e));
         }
         return this;
      }

      @Override
      public Task build() {
         MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask
               = new MapReduceTask<KIn, VIn, KOut, VOut>(cache, distributedReducePhase, useIntermediateSharedCache);
         mapReduceTask.mappedWith(mapper).reducedWith(reducer).combinedWith(combiner);
         return new Task(mapReduceTask, collator);
      }
   }

   @Override
   protected Builder builder(Cache<KIn, VIn> cache) {
      return new Builder(cache);
   }

   @Override
   public boolean supportsIntermediateSharedCache() {
      return true;
   }

   @Override
   public boolean supportsDistributedReducePhase() {
      return true;
   }

   @Override
   public boolean supportsCombiner() {
      return true;
   }
}
