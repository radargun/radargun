package org.radargun.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.distexec.mapreduce.Collator;
import org.infinispan.distexec.mapreduce.MapReduceTask;
import org.infinispan.distexec.mapreduce.Mapper;
import org.infinispan.distexec.mapreduce.Reducer;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.MapReducer;
import org.radargun.utils.Utils;

public class InfinispanMapReduce<KIn, VIn, KOut, VOut, R> implements MapReducer<KOut, VOut, R> {
   protected Log log = LogFactory.getLog(getClass());

   protected Infinispan51EmbeddedService service;

   public InfinispanMapReduce(Infinispan51EmbeddedService service) {
      this.service = service;
   }

   protected class Builder implements MapReducer.Builder<KOut, VOut, R> {
      protected final Cache<KIn, VIn> cache;
      protected ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      protected Collator<KOut, VOut, R> collator = null;
      protected Mapper<KIn, VIn, KOut, VOut> mapper = null;
      protected Reducer<KOut, VOut> reducer = null;

      public Builder(Cache<KIn, VIn> cache) {
         this.cache = cache;
      }

      @Override
      public Builder distributedReducePhase(boolean distributedReducePhase) {
         throw new UnsupportedOperationException("Distributed reduce phase not supported");
      }

      @Override
      public Builder useIntermediateSharedCache(boolean useIntermediateSharedCache) {
         throw new UnsupportedOperationException("Intermediate shared cache not supported");
      }

      @Override
      public Builder timeout(long timeout, TimeUnit unit) {
         throw new UnsupportedOperationException("Timeout not supported");
      }

      @Override
      public Builder resultCacheName(String resultCacheName) {
         throw new UnsupportedOperationException("Result cache not supported");
      }

      @Override
      public Builder mapper(String mapperFqn, Map<String, String> mapperParameters) {
         try {
            mapper = Utils.instantiate(mapperFqn);
            Utils.invokeMethodWithString(mapper, mapperParameters);
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate Mapper class: " + mapperFqn, e);
         }
         return this;
      }

      @Override
      public Builder reducer(String reducerFqn, Map<String, String> reducerParameters) {
         try {
            reducer = Utils.instantiate(reducerFqn);
            Utils.invokeMethodWithString(reducer, reducerParameters);
         } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate Reducer class: " + reducerFqn, e);
         }
         return this;
      }

      @Override
      public Builder combiner(String combinerFqn, Map<String, String> combinerParameters) {
         throw new UnsupportedOperationException("Combiner not supported");
      }

      @Override
      public Builder collator(String collatorFqn, Map<String, String> collatorParameters) {
         try {
            collator = Utils.instantiate(collatorFqn);
            Utils.invokeMethodWithString(collator, collatorParameters);
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
   public Builder builder(String cacheName) {
      @SuppressWarnings("unchecked")
      Cache<KIn, VIn> cache = (Cache<KIn, VIn>) service.getCache(cacheName);
      return builder(cache);
   }

   protected Builder builder(Cache<KIn, VIn> cache) {
      return new Builder(cache);
   }

   @Override
   public boolean supportsResultCacheName() {
      return false;
   }

   @Override
   public boolean supportsIntermediateSharedCache() {
      return false;
   }

   @Override
   public boolean supportsCombiner() {
      return false;
   }

   @Override
   public boolean supportsTimeout() {
      return false;
   }

   @Override
   public boolean supportsDistributedReducePhase() {
      return false;
   }
}
