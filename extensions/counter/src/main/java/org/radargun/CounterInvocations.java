package org.radargun;

import org.radargun.stages.test.Invocation;
import org.radargun.traits.CounterOperations;


/**
 * Provides {@link Invocation} implementations for operations from traits
 * {@link CounterOperations}
 *
 * @author Martin Gencur
 */
public class CounterInvocations {
   public static final class IncrementAndGet implements Invocation<Long> {
      private final CounterOperations.Counter counter;

      public IncrementAndGet(CounterOperations.Counter counter) {
         this.counter = counter;
      }

      @Override
      public Long invoke() {
         try {
            return counter.incrementAndGet();
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               CounterOperations.INCREMENT_AND_GET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return CounterOperations.INCREMENT_AND_GET;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   public static final class DecrementAndGet implements Invocation<Long> {
      private final CounterOperations.Counter counter;

      public DecrementAndGet(CounterOperations.Counter counter) {
         this.counter = counter;
      }

      @Override
      public Long invoke() {
         try {
            return counter.decrementAndGet();
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               CounterOperations.DECREMENT_AND_GET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return CounterOperations.DECREMENT_AND_GET;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   public static final class AddAndGet implements Invocation<Long> {
      private final CounterOperations.Counter counter;
      private final long delta;

      public AddAndGet(CounterOperations.Counter counter, long delta) {
         this.counter = counter;
         this.delta = delta;
      }

      @Override
      public Long invoke() {
         try {
            return counter.addAndGet(delta);
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               CounterOperations.ADD_AND_GET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return CounterOperations.ADD_AND_GET;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }

   public static final class CompareAndSet implements Invocation<Boolean> {
      private final CounterOperations.Counter counter;
      private final long expect;
      private final long update;

      public CompareAndSet(CounterOperations.Counter counter, long expect, long update) {
         this.counter = counter;
         this.expect = expect;
         this.update = update;
      }

      @Override
      public Boolean invoke() {
         try {
            return counter.compareAndSet(expect, update);
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               CounterOperations.COMPARE_AND_SET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return CounterOperations.COMPARE_AND_SET;
      }

      @Override
      public Operation txOperation() {
         throw new UnsupportedOperationException("There is no transactional operation");
      }

      @Override
      public Object getTxResource() {
         throw new UnsupportedOperationException("getTxResource is supposed to be used when we need transactional support");
      }
   }
}
