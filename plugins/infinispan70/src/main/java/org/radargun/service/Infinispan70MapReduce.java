package org.radargun.service;

import java.util.Map;

import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Mapper;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.utils.ClassLoadHelper;
import org.radargun.utils.Utils;

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

   @SuppressWarnings("unchecked")
   @Override
   public Map<KOut, VOut> executeMapReduceTask(ClassLoadHelper classLoadHelper, String mapperFqn, String reducerFqn) {
      MapReduceTask<KIn, VIn, KOut, VOut> t = mapReduceTaskFactory();

      Mapper<KIn, VIn, KOut, VOut> mapper = null;
      Reducer<KOut, VOut> reducer = null;

      Map<KOut, VOut> result = null;

      try {
         mapper = (Mapper<KIn, VIn, KOut, VOut>) classLoadHelper.createInstance(mapperFqn);
         t = t.mappedWith(mapper);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e));
      }

      try {
         reducer = (Reducer<KOut, VOut>) classLoadHelper.createInstance(reducerFqn);
         t = t.reducedWith(reducer);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e));
      }

      if (mapper != null && reducer != null) {
         Utils.invokeMethodWithString(mapper, this.mapperParameters);
         Utils.invokeMethodWithString(reducer, this.reducerParameters);
         if (resultCacheName != null) {
            t.execute(resultCacheName);
         } else {
            result = t.execute();
         }
      }
      return result;
   }

}
