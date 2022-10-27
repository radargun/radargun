package org.radargun.service;

import java.util.Collection;
import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.Collator;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Mapper;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.MapReducer;
import org.radargun.utils.KeyValueProperty;
import org.radargun.utils.Utils;

public class InfinispanMapReduce<KIn, VIn, KOut, VOut, R> implements MapReducer<KOut, VOut, R> {
   protected Log log = LogFactory.getLog(getClass());

   protected Infinispan51EmbeddedService service;

   public InfinispanMapReduce(Infinispan51EmbeddedService service) {
      this.service = service;
   }

   protected class Builder implements MapReducer.Builder<KOut, VOut, R> {
      protected Cache<KIn, VIn> cache;
      protected Collator<KOut, VOut, R> collator = null;
      protected Mapper<KIn, VIn, KOut, VOut> mapper = null;
      protected Reducer<KOut, VOut> reducer = null;

      @Override
      public Builder timeout(long timeout) {
         throw new UnsupportedOperationException("Timeout not supported");
      }

      @Override
      public MapReducer.Builder<KOut, VOut, R> source(String source) {
         cache = (Cache<KIn, VIn>) service.getCache(source);
         return this;
      }

      @Override
      public Builder mapper(String mapperFqn, Collection<KeyValueProperty> mapperParameters) {
         try {
            mapper = Utils.instantiate(mapperFqn);
            Utils.invokeMethodWithProperties(mapper, mapperParameters);
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e);
         }
         return this;
      }

      @Override
      public Builder reducer(String reducerFqn, Collection<KeyValueProperty> reducerParameters) {
         try {
            reducer = Utils.instantiate(reducerFqn);
            Utils.invokeMethodWithProperties(reducer, reducerParameters);
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e);
         }
         return this;
      }

      @Override
      public Builder combiner(String combinerFqn, Collection<KeyValueProperty> combinerParameters) {
         throw new UnsupportedOperationException("Combiner not supported");
      }

      @Override
      public Builder collator(String collatorFqn, Collection<KeyValueProperty> collatorParameters) {
         try {
            collator = Utils.instantiate(collatorFqn);
            Utils.invokeMethodWithProperties(collator, collatorParameters);
         } catch (Exception e) {
            throw (new IllegalArgumentException("Could not instantiate Collator class: " + collatorFqn, e));
         }
         return this;
      }

      @Override
      public Task build() {
         MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask = new MapReduceTask<KIn, VIn, KOut, VOut>(cache);
         mapReduceTask.mappedWith(mapper).reducedWith(reducer);
         return new Task(mapReduceTask, collator);
      }
   }

   protected class Task implements MapReducer.Task<KOut, VOut, R> {
      protected final Collator<KOut, VOut, R> collator;
      protected final MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask;

      public Task(MapReduceTask<KIn, VIn, KOut, VOut> mapReduceTask, Collator<KOut, VOut, R> collator) {
         this.mapReduceTask = mapReduceTask;
         this.collator = collator;
      }

      @Override
      public Map<KOut, VOut> execute() {
         return mapReduceTask.execute();
      }

      @Override
      public R executeWithCollator() {
         return mapReduceTask.execute(collator);
      }
   }

   @Override
   public MapReducer.Builder<KOut, VOut, R> builder() {
      return new Builder();
   }

   @Override
   public boolean supportsCombiner() {
      return false;
   }

   @Override
   public boolean supportsTimeout() {
      return false;
   }

}
