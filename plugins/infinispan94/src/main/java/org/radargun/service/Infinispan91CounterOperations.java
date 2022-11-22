package org.radargun.service;

import org.infinispan.counter.EmbeddedCounterManagerFactory;
import org.infinispan.counter.api.CounterManager;
import org.radargun.traits.CounterOperations;

/**
 * @author Martin Gencur
 */
public class Infinispan91CounterOperations implements CounterOperations {

   protected final InfinispanEmbeddedService service;

   public Infinispan91CounterOperations(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public Counter getCounter(String name) {
      CounterManager counterManager = EmbeddedCounterManagerFactory.asCounterManager(service.getCacheManager());
      return new CounterImpl(counterManager.getStrongCounter(name));
   }

   protected static class CounterImpl implements Counter {

      org.infinispan.counter.api.StrongCounter counter;

      public CounterImpl(org.infinispan.counter.api.StrongCounter counter) {
         this.counter = counter;
      }

      @Override
      public long getValue() throws Exception {
         return counter.getValue().get();
      }

      @Override
      public long incrementAndGet() throws Exception {
         return counter.incrementAndGet().get();
      }

      @Override
      public long decrementAndGet() throws Exception {
         return counter.decrementAndGet().get();
      }

      @Override
      public long addAndGet(long delta)  throws Exception {
         return counter.addAndGet(delta).get();
      }

      @Override
      public boolean compareAndSet(long expect, long update) throws Exception {
         return counter.compareAndSet(expect, update).get();
      }
   }
}
