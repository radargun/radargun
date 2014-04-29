package org.radargun.stats;

import java.io.Serializable;

/**
 * Records request for single operation and converts the data stored into requested representation.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface OperationStats extends Serializable {
   /**
    * @return Deep copy of this instance.
    */
   OperationStats copy();

   /**
    * Adds the data stored by another instance to this instance.
    * @param other Must be of the same class as this instance.
    */
   void merge(OperationStats other);

   /**
    * Register response latency of successful operation.
    * @param responseTime
    */
   void registerRequest(long responseTime);

   /**
    * Register response latency of failed operation.
    * @param responseTime
    */
   void registerError(long responseTime);

   /**
    * Convert the internal state into requested representation.
    * @param clazz
    * @param <T>
    * @return The representation, or null if this class is not capable of requested conversion.
    */
   <T> T getRepresentation(Class<T> clazz);

   /**
    * @return True if no request was recorded.
    */
   boolean isEmpty();
}
