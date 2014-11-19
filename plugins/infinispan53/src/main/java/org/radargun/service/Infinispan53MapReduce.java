package org.radargun.service;

import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.MapReduceTask;

public class Infinispan53MapReduce<KIn, VIn, KOut, VOut, R> extends Infinispan52MapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan53MapReduce(Infinispan53EmbeddedService service) {
      super(service);
   }

   @Override
   public boolean setTimeout(long timeout, TimeUnit unit) {
      this.timeout = timeout;
      this.unit = unit;
      return true;
   }

   @Override
   protected MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTaskFactory() {
      @SuppressWarnings("unchecked")
      Cache<KIn, VIn> cache = (Cache<KIn, VIn>) service.getCache(null);
      MapReduceTask<KIn, VIn, KOut, VOut> task = new MapReduceTask<KIn, VIn, KOut, VOut>(cache,
            this.distributeReducePhase, this.useIntermediateSharedCache);
      task.timeout(timeout, unit);
      return task;
   }
}
