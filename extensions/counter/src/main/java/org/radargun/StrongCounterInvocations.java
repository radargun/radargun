package org.radargun;

import org.radargun.stages.test.Invocation;
import org.radargun.traits.StrongCounterOperations;


/**
 * Provides {@link Invocation} implementations for operations from traits
 * {@link StrongCounterOperations}
 *
 * @author Martin Gencur
 */
public class StrongCounterInvocations {
   public static final class IncrementAndGet implements Invocation<Long> {
      private final StrongCounterOperations.StrongCounter strongCounter;

      public IncrementAndGet(StrongCounterOperations.StrongCounter strongCounter) {
         this.strongCounter = strongCounter;
      }

      @Override
      public Long invoke() {
         try {
            return strongCounter.incrementAndGet().get();
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               StrongCounterOperations.INCREMENT_AND_GET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return StrongCounterOperations.INCREMENT_AND_GET;
      }

      @Override
      public Operation txOperation() {
         return StrongCounterOperations.INCREMENT_AND_GET;
      }
   }

   public static final class DecrementAndGet implements Invocation<Long> {
      private final StrongCounterOperations.StrongCounter strongCounter;

      public DecrementAndGet(StrongCounterOperations.StrongCounter strongCounter) {
         this.strongCounter = strongCounter;
      }

      @Override
      public Long invoke() {
        try {
            return strongCounter.decrementAndGet().get();
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               StrongCounterOperations.DECREMENT_AND_GET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return StrongCounterOperations.DECREMENT_AND_GET;
      }

      @Override
      public Operation txOperation() {
         return StrongCounterOperations.DECREMENT_AND_GET;
      }
   }

   public static final class AddAndGet implements Invocation<Long> {
      private final StrongCounterOperations.StrongCounter strongCounter;
      private final long delta;

      public AddAndGet(StrongCounterOperations.StrongCounter strongCounter, long delta) {
         this.strongCounter = strongCounter;
         this.delta = delta;
      }

      @Override
      public Long invoke() {
         try {
            return strongCounter.addAndGet(delta).get();
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               StrongCounterOperations.ADD_AND_GET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return StrongCounterOperations.ADD_AND_GET;
      }

      @Override
      public Operation txOperation() {
         return StrongCounterOperations.ADD_AND_GET;
      }
   }

   public static final class CompareAndSet implements Invocation<Boolean> {
      private final StrongCounterOperations.StrongCounter strongCounter;
      private final long expect;
      private final long update;

      public CompareAndSet(StrongCounterOperations.StrongCounter strongCounter, long expect, long update) {
         this.strongCounter = strongCounter;
         this.expect = expect;
         this.update = update;
      }

      @Override
      public Boolean invoke() {
         try {
            return strongCounter.compareAndSet(expect, update).get();
         } catch (Exception e) {
            throw new RuntimeException("Operation " +
               StrongCounterOperations.COMPARE_AND_SET.toString() + "failed", e);
         }
      }

      @Override
      public Operation operation() {
         return StrongCounterOperations.COMPARE_AND_SET;
      }

      @Override
      public Operation txOperation() {
         return StrongCounterOperations.COMPARE_AND_SET;
      }
   }
}
