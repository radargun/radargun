package org.radargun.service;

import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Mapper;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.service.InfinispanMapReduce.InfinispanMapReduceTask;
import org.radargun.traits.MapReducer.MapTask;
import org.radargun.utils.Utils;

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

   @Override
   public MapTask<KOut, VOut, R> configureMapReduceTask(String mapperFqn, Map<String, String> mapperParameters,
         String reducerFqn, Map<String, String> reducerParameters) {
      MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask = mapReduceTaskFactory();

      Mapper<KIn, VIn, KOut, VOut> mapper = null;
      Reducer<KOut, VOut> reducer = null;

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      try {
         mapper = Utils.instantiate(classLoader, mapperFqn);
         mapReduceTask = mapReduceTask.mappedWith(mapper);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e));
      }

      try {
         reducer = Utils.instantiate(classLoader, reducerFqn);
         mapReduceTask = mapReduceTask.reducedWith(reducer);
      } catch (Exception e) {
         throw (new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e));
      }

      if (mapper != null && reducer != null) {
         Utils.invokeMethodWithString(mapper, mapperParameters);
         Utils.invokeMethodWithString(reducer, reducerParameters);
      }

      return new Infinispan70MapReduceTask(mapReduceTask);
   }

}
