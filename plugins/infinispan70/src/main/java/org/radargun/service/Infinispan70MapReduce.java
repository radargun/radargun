package org.radargun.service;

import java.util.Map;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class Infinispan70MapReduce<KIn, VIn, KOut, VOut, R> extends Infinispan53MapReduce<KIn, VIn, KOut, VOut, R> {

   public Infinispan70MapReduce(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   public Map<KOut, VOut> executeMapReduceTask() {
      Map<KOut, VOut> result = null;
      //      statistics.begin();
      if (resultCacheName != null) {
         mapReduceTask.execute(resultCacheName);
      } else {
         result = mapReduceTask.execute();
      }
      //      statistics.end();
      //      statistics.registerRequest(statistics.getEnd() - statistics.getBegin(), MapReducer.MAPREDUCE);
      return result;
   }

}
