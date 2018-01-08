package org.radargun.service;

import com.hazelcast.core.IAtomicLong;
import org.radargun.traits.CounterOperations;

/**
 * @author Martin Gencur
 */
public class Hazelcast39CounterOperations implements CounterOperations {

   protected final HazelcastService service;

   public Hazelcast39CounterOperations(HazelcastService service) {
      this.service = service;
   }

   @Override
   public Counter getCounter(String name) {
      return new CounterImpl(service.hazelcastInstance.getAtomicLong(name));
   }

   protected static class CounterImpl implements Counter {

      IAtomicLong counter;

      public CounterImpl(IAtomicLong counter) {
         this.counter = counter;
      }

      @Override
      public long getValue() throws Exception {
         return counter.get();
      }

      @Override
      public long incrementAndGet() {
         return counter.incrementAndGet();
      }

      @Override
      public long decrementAndGet() {
         return counter.decrementAndGet();
      }

      @Override
      public long addAndGet(long delta) {
         return counter.addAndGet(delta);
      }

      @Override
      public boolean compareAndSet(long expect, long update) {
         return counter.compareAndSet(expect, update);
      }
   }
}
