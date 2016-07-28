package org.radargun.stages.test;

import org.radargun.Operation;

/**
 * Represent an operation that the {@link LegacyStressor}
 * should execute and record its duration.
 */
public interface Invocation<T> {
   /**
    * Invoke the operation.
    */
   T invoke();

   /**
    * Operation that was executed.
    * @return
    */
   Operation operation();

   /**
    * Operation variant if this was executed within transaction.
    * @return
    * @deprecated only applicable for {@link LegacyStressor}
    */
   @Deprecated
   Operation txOperation();
}
