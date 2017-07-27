package org.radargun.service;

import java.util.concurrent.CompletableFuture;
import org.infinispan.counter.EmbeddedCounterManagerFactory;
import org.infinispan.counter.api.CounterManager;
import org.radargun.traits.StrongCounterOperations;

/**
 * @author Martin Gencur
 */
public class Infinispan90StrongCounterOperations implements StrongCounterOperations {

   protected final InfinispanEmbeddedService service;

   public Infinispan90StrongCounterOperations(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public StrongCounter getStrongCounter(String name) {
      CounterManager counterManager = EmbeddedCounterManagerFactory.asCounterManager(service.getCacheManager());
      return new StrongCounterImpl(counterManager.getStrongCounter(name));
   }

   protected static class StrongCounterImpl implements StrongCounterOperations.StrongCounter {

      org.infinispan.counter.api.StrongCounter counter;

      public StrongCounterImpl(org.infinispan.counter.api.StrongCounter counter) {
         this.counter = counter;
      }

      @Override
      public CompletableFuture<Long> incrementAndGet() {
         return counter.incrementAndGet();
      }

      @Override
      public CompletableFuture<Long> decrementAndGet() {
         return counter.decrementAndGet();
      }

      @Override
      public CompletableFuture<Long> addAndGet(long delta) {
         return counter.addAndGet(delta);
      }

      @Override
      public CompletableFuture<Boolean> compareAndSet(long expect, long update) {
         return counter.compareAndSet(expect, update);
      }
   }
}
