package org.radargun.service;

import java.util.concurrent.CompletableFuture;

import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.ICompletableFuture;

class CompletableFutureAdapter<T> extends CompletableFuture<T> implements ExecutionCallback<T> {

   public static <T> CompletableFuture<T> from(ICompletableFuture<T> icf) {
      CompletableFutureAdapter<T> cf = new CompletableFutureAdapter<>();
      icf.andThen(cf);
      return cf;
   }

   @Override
   public void onResponse(T t) {
      complete(t);
   }

   @Override
   public void onFailure(Throwable throwable) {
      completeExceptionally(throwable);
   }
}
