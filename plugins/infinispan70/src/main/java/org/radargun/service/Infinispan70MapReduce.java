package org.radargun.service;

import org.infinispan.distexec.mapreduce.MapReduceTask;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class Infinispan70MapReduce<KIn, VIn, KOut, VOut, R> extends Infinispan53MapReduce<KIn, VIn, KOut, VOut, R> {

   private int maxCollectorSize = -1;

   public Infinispan70MapReduce(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   public boolean setMaxCollectorSize(int maxCollectorSize) {
      this.maxCollectorSize = maxCollectorSize;
      return true;
   }

   @Override
   protected MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTaskFactory() {
      MapReduceTask<KIn, VIn, KOut, VOut> task = super.mapReduceTaskFactory();
      if (this.maxCollectorSize != -1) {
         task.setMaxCollectorSize(this.maxCollectorSize);
      }
      return task;
   }
}
