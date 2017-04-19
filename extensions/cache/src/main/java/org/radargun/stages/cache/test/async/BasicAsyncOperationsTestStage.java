package org.radargun.stages.cache.test.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.radargun.Operation;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.stages.test.Blackhole;
import org.radargun.stages.test.async.Conversation;
import org.radargun.stages.test.async.SchedulingSelector;
import org.radargun.stats.Request;
import org.radargun.stats.Statistics;
import org.radargun.traits.BasicAsyncOperations;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;

@Stage(doc = "Tests basic operations using the async API. Uses single-operation conversations.")
public class BasicAsyncOperationsTestStage extends CacheAsyncTestStage {

   @PropertyDelegate(prefix = "get.")
   protected InvocationSetting get = new InvocationSetting();

   @PropertyDelegate(prefix = "containsKey.")
   protected InvocationSetting containsKey = new InvocationSetting();

   @PropertyDelegate(prefix = "put.")
   protected InvocationSetting put = new InvocationSetting();

   @PropertyDelegate(prefix = "getAndPut.")
   protected InvocationSetting getAndPut = new InvocationSetting();

   @PropertyDelegate(prefix = "remove.")
   protected InvocationSetting remove = new InvocationSetting();

   @PropertyDelegate(prefix = "getAndRemove.")
   protected InvocationSetting getAndRemove = new InvocationSetting();

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   BasicAsyncOperations basicAsyncOperations;

   protected BasicAsyncOperations.Cache cache;

   @Override
   protected void prepare() {
      super.prepare();
      cache = basicAsyncOperations.getCache(cacheSelector.getCacheName(-1));
   }

   @Override
   protected SchedulingSelector<Conversation> createSelector() {
      return new SchedulingSelector.Builder<>(Conversation.class)
            .add(new Get(), get.invocations, get.interval)
            .add(new ContainsKey(), containsKey.invocations, containsKey.interval)
            .add(new Put(), put.invocations, put.interval)
            .add(new GetAndPut(), getAndPut.invocations, getAndPut.interval)
            .add(new Remove(), remove.invocations, remove.interval)
            .add(new GetAndRemove(), getAndRemove.invocations, getAndRemove.interval)
            .build();
   }

   private <T> CompletableFuture<T> makeRequest(Supplier<CompletableFuture<T>> invocation, Statistics stats, Operation op) {
      Request request = stats.startRequest();
      try {
         CompletableFuture<T> cf = invocation.get();
         request.requestCompleted();
         cf.whenComplete((response, ex) -> {
            if (ex == null) {
               request.succeeded(op);
               Blackhole.consume(response);
            } else {
               request.failed(op);
               log.error("Request failed during invocation", ex);
            }
         });
         return cf;
      } catch (Exception e) {
         request.requestFailed();
         CompletableFuture<T> cf = new CompletableFuture();
         cf.completeExceptionally(e);
         log.error("Request failed during processing", e);
         return cf;
      }
   }

   protected class Get<V> implements Conversation, Supplier<CompletableFuture<V>> {
      @Override
      public void start(Statistics stats) {
         makeRequest(this, stats, BasicOperations.GET);
      }

      @Override
      public CompletableFuture<V> get() {
         return cache.get(getRandomKey(ThreadLocalRandom.current()));
      }
   }

   protected class ContainsKey implements Conversation, Supplier<CompletableFuture<Boolean>> {
      @Override
      public void start(Statistics stats) {
         makeRequest(this, stats, BasicOperations.CONTAINS_KEY);
      }

      @Override
      public CompletableFuture<Boolean> get() {
         return cache.containsKey(getRandomKey(ThreadLocalRandom.current()));
      }
   }

   protected class Put implements Conversation, Supplier<CompletableFuture<Void>> {
      @Override
      public void start(Statistics stats) {
         makeRequest(this, stats, BasicOperations.PUT);
      }

      @Override
      public CompletableFuture<Void> get() {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         Object key = getRandomKey(random);
         Object value = getRandomValue(random, key);
         return cache.put(key, value);
      }
   }

   protected class GetAndPut<V> implements Conversation, Supplier<CompletableFuture<V>> {
      @Override
      public void start(Statistics stats) {
         makeRequest(this, stats, BasicOperations.GET_AND_PUT);
      }

      @Override
      public CompletableFuture<V> get() {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         Object key = getRandomKey(random);
         Object value = getRandomValue(random, key);
         return cache.getAndPut(key, value);
      }
   }

   protected class Remove implements Conversation, Supplier<CompletableFuture<Void>> {
      @Override
      public void start(Statistics stats) {
         makeRequest(this, stats, BasicOperations.REMOVE);
      }

      @Override
      public CompletableFuture<Void> get() {
         return cache.remove(getRandomKey(ThreadLocalRandom.current()));
      }
   }

   protected class GetAndRemove<V> implements Conversation, Supplier<CompletableFuture<V>> {
      @Override
      public void start(Statistics stats) {
         makeRequest(this, stats, BasicOperations.GET_AND_REMOVE);
      }

      @Override
      public CompletableFuture<V> get() {
         return cache.getAndRemove(getRandomKey(ThreadLocalRandom.current()));
      }
   }
}
