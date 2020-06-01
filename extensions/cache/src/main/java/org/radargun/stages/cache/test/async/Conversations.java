package org.radargun.stages.cache.test.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.radargun.Operation;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.stages.test.Blackhole;
import org.radargun.stats.Request;
import org.radargun.stats.RequestSet;
import org.radargun.traits.BasicOperations;
import org.radargun.utils.TimeConverter;

class Conversations {
   private Conversations() {}

   protected abstract static class RecordingConversationStep implements ConversationStep {
      public abstract CompletableFuture<?> exec(ConversationContext ctx);
      public abstract Operation operation();

      @Override
      public CompletableFuture<?> apply(ConversationContext context) {
         Request request = context.statistics().startRequest();
         try {
            CompletableFuture<?> cf = exec(context);
            request.requestCompleted();
            cf.whenComplete((response, ex) -> {
               if (ex == null) {
                  request.succeeded(operation());
                  Blackhole.consume(response);
               } else {
                  request.failed(operation());
               }
               RequestSet requestSet = context.requestSet();
               synchronized (requestSet) {
                  requestSet.add(request);
               }
            });
            return cf;
         } catch (Exception e) {
            request.requestFailed();
            RequestSet requestSet = context.requestSet();
            synchronized (requestSet) {
               requestSet.add(request);
            }
            CompletableFuture<Object> cf = new CompletableFuture();
            cf.completeExceptionally(e);
            return cf;
         }
      }
   }

   @DefinitionElement(name = "get", doc = "cache.get()")
   public static class Get extends RecordingConversationStep {
      @Override
      public CompletableFuture<Object> exec(ConversationContext ctx) {
         return ctx.cache().get(ctx.provider().getRandomKey(ThreadLocalRandom.current()));
      }

      @Override
      public Operation operation() {
         return BasicOperations.GET;
      }
   }

   @DefinitionElement(name = "contains-key", doc = "cache.containsKey()")
   public static class ContainsKey extends RecordingConversationStep {
      @Override
      public CompletableFuture<Boolean> exec(ConversationContext ctx) {
         return ctx.cache().containsKey(ctx.provider().getRandomKey(ThreadLocalRandom.current()));
      }

      @Override
      public Operation operation() {
         return BasicOperations.CONTAINS_KEY;
      }
   }

   @DefinitionElement(name = "put", doc = "cache.put()")
   public static class Put extends RecordingConversationStep {
      public CompletableFuture<Void> exec(ConversationContext ctx) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         ContextProvider provider = ctx.provider();
         Object key = provider.getRandomKey(random);
         Object value = provider.getRandomValue(random, key);
         return provider.cache().put(key, value);
      }

      @Override
      public Operation operation() {
         return BasicOperations.PUT;
      }
   }

   @DefinitionElement(name = "get-and-put", doc = "cache.getAndPut()")
   public static class GetAndPut extends RecordingConversationStep {
      public CompletableFuture<Object> exec(ConversationContext ctx) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         ContextProvider provider = ctx.provider();
         Object key = provider.getRandomKey(random);
         Object value = provider.getRandomValue(random, key);
         return provider.cache().getAndPut(key, value);
      }

      @Override
      public Operation operation() {
         return BasicOperations.GET_AND_PUT;
      }
   }

   @DefinitionElement(name = "remove", doc = "cache.remove()")
   public static class Remove extends RecordingConversationStep {
      public CompletableFuture<Boolean> exec(ConversationContext ctx) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         ContextProvider provider = ctx.provider();
         return provider.cache().remove(provider.getRandomKey(random));
      }

      @Override
      public Operation operation() {
         return BasicOperations.REMOVE;
      }
   }

   @DefinitionElement(name = "get-and-remove", doc = "cache.remove()")
   public static class GetAndRemove extends RecordingConversationStep {
      public CompletableFuture<Object> exec(ConversationContext ctx) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         ContextProvider provider = ctx.provider();
         return provider.cache().getAndRemove(provider.getRandomKey(random));
      }

      @Override
      public Operation operation() {
         return BasicOperations.REMOVE;
      }
   }

   @DefinitionElement(name = "pause", doc = "Delay conversation for given time.")
   public static class Pause implements ConversationStep {
      @Property(doc = "Delay in milliseconds", converter = TimeConverter.class, optional = false)
      protected long delay;

      @Override
      public CompletableFuture<Void> apply(ConversationContext context) {
         SelfCompletingFuture df = new SelfCompletingFuture();
         context.provider().scheduledExecutor().schedule(df, delay, TimeUnit.MILLISECONDS);
         return df;
      }
   }

   private static class SelfCompletingFuture extends CompletableFuture<Void> implements Runnable {
      @Override
      public void run() {
         this.complete(null);
      }
   }
}
