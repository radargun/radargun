package org.radargun.service;

import java.util.Collection;

import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.traits.MapReducer;
import org.radargun.utils.KeyValueProperty;
import org.radargun.utils.Utils;

public class Infinispan52MapReduce<KIn, VIn, KOut, VOut, R> extends InfinispanMapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan52MapReduce(Infinispan52EmbeddedService service) {
      super(service);
   }

   protected class Builder extends InfinispanMapReduce<KIn, VIn, KOut, VOut, R>.Builder {
      protected Reducer<KOut, VOut> combiner;

      @Override
      public Builder combiner(String combinerFqn, Collection<KeyValueProperty> combinerParameters) {
         try {
            combiner = Utils.instantiate(combinerFqn);
            Utils.invokeMethodWithProperties(combiner, combinerParameters);
         } catch (Exception e) {
            throw (new IllegalArgumentException("Could not instantiate Combiner class: " + combinerFqn, e));
         }
         return this;
      }

      @Override
      public Task build() {
         Infinispan52EmbeddedService embeddedService = (Infinispan52EmbeddedService) service;
         MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask
            = new MapReduceTask<KIn, VIn, KOut, VOut>(cache, embeddedService.mapReduceDistributedReducePhase, embeddedService.mapReduceUseIntermediateSharedCache);
         mapReduceTask.mappedWith(mapper).reducedWith(reducer).combinedWith(combiner);
         return new Task(mapReduceTask, collator);
      }
   }

   @Override
   public MapReducer.Builder<KOut, VOut, R> builder() {
      return new Builder();
   }

   @Override
   public boolean supportsCombiner() {
      return true;
   }
}
