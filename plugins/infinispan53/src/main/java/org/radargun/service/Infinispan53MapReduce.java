package org.radargun.service;

import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;

public class Infinispan53MapReduce<KIn, VIn, KOut, VOut, R> extends Infinispan52MapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan53MapReduce(Infinispan53EmbeddedService service) {
      super(service);
   }

   protected class Builder extends Infinispan52MapReduce<KIn, VIn, KOut, VOut, R>.Builder {
      protected long timeout;
      protected TimeUnit unit;

      public Builder(Cache<KIn, VIn> cache) {
         super(cache);
      }

      @Override
      public Builder timeout(long timeout, TimeUnit unit) {
         this.timeout = timeout;
         this.unit = unit;
         return this;
      }

      @Override
      public Task build() {
         Task task = super.build();
         task.mapReduceTask.timeout(timeout, unit);
         return task;
      }
   }

   @Override
   protected Builder builder(Cache<KIn, VIn> cache) {
      return new Builder(cache);
   }

   @Override
   public boolean supportsTimeout() {
      return true;
   }
}
