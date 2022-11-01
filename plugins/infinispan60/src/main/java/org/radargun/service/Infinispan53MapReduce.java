package org.radargun.service;

import java.util.concurrent.TimeUnit;

import org.radargun.traits.MapReducer;

public class Infinispan53MapReduce<KIn, VIn, KOut, VOut, R> extends Infinispan52MapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan53MapReduce(Infinispan53EmbeddedService service) {
      super(service);
   }

   protected class Builder extends Infinispan52MapReduce<KIn, VIn, KOut, VOut, R>.Builder {
      protected long timeout;

      @Override
      public Builder timeout(long timeout) {
         this.timeout = timeout;
         return this;
      }

      @Override
      public Task build() {
         Task task = super.build();
         task.mapReduceTask.timeout(timeout, TimeUnit.MILLISECONDS);
         return task;
      }
   }

   @Override
   public MapReducer.Builder<KOut, VOut, R> builder() {
      return new Builder();
   }

   @Override
   public boolean supportsTimeout() {
      return true;
   }
}
