package org.radargun.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.ICompletableFuture;

class MappingCompletableFutureAdapter<T1, T2> extends CompletableFuture<T2> implements ExecutionCallback<T1> {
   private final Function<T1, T2> function;

   public MappingCompletableFutureAdapter(Function<T1, T2> function) {
      this.function = function;
   }

   @Override
   public void onResponse(T1 t) {
      complete(function.apply(t));
   }

   @Override
   public void onFailure(Throwable throwable) {
      completeExceptionally(throwable);
   }

   public static <T1, T2> CompletableFuture<T2> from(ICompletableFuture<T1> icf, Function<T1, T2> function) {
      MappingCompletableFutureAdapter<T1, T2> cf = new MappingCompletableFutureAdapter<>(function);
      icf.andThen(cf);
      return cf;
   }
}
