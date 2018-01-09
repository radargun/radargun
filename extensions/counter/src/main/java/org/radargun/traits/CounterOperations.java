package org.radargun.traits;

import org.radargun.Operation;

/**
 * @author Martin Gencur
 */
@Trait(doc = "Counter operations.")
public interface CounterOperations {
   String TRAIT = CounterOperations.class.getSimpleName();

   Operation GET_VALUE = Operation.register(TRAIT + ".GetValue");
   Operation INCREMENT_AND_GET = Operation.register(TRAIT + ".IncrementAndGet");
   Operation DECREMENT_AND_GET = Operation.register(TRAIT + ".DecrementAndGet");
   Operation ADD_AND_GET = Operation.register(TRAIT + ".AddAndGet");
   Operation COMPARE_AND_SET = Operation.register(TRAIT + ".CompareAndSet");

   Counter getCounter(String name);

   interface Counter {
      long getValue() throws Exception;

      long incrementAndGet() throws Exception;

      long decrementAndGet() throws Exception;

      long addAndGet(long delta) throws Exception;

      boolean compareAndSet(long expect, long update) throws Exception;
   }
}
