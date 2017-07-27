package org.radargun.traits;

import java.util.concurrent.CompletableFuture;
import org.radargun.Operation;

/**
 * @author Martin Gencur
 */
@Trait(doc = "Strong counter operations.")
public interface StrongCounterOperations {
   String TRAIT = StrongCounterOperations.class.getSimpleName();

   Operation INCREMENT_AND_GET = Operation.register(TRAIT + ".IncrementAndGet");
   Operation DECREMENT_AND_GET = Operation.register(TRAIT + ".DecrementAndGet");
   Operation ADD_AND_GET = Operation.register(TRAIT + ".AddAndGet");
   Operation COMPARE_AND_SET = Operation.register(TRAIT + ".CompareAndSet");

   StrongCounter getStrongCounter(String name);

   interface StrongCounter {
      CompletableFuture<Long> incrementAndGet();

      CompletableFuture<Long> decrementAndGet();

      CompletableFuture<Long> addAndGet(long delta);

      CompletableFuture<Boolean> compareAndSet(long expect, long update);
   }
}
