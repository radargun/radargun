package org.radargun.service;

import java.util.Map;

import org.infinispan.distexec.mapreduce.MapReduceTask;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class Infinispan70MapReduce<KIn, VIn, KOut, VOut, R> extends Infinispan53MapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan70MapReduce(Infinispan60EmbeddedService service) {
      super(service);
   }
   
   class Infinispan70MapReduceTask extends InfinispanMapReduceTask {

      public Infinispan70MapReduceTask(MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask) {
         super(mapReduceTask);
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

}
