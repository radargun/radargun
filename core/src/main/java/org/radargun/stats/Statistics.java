package org.radargun.stats;

import java.io.Serializable;
import java.util.Map;

import org.radargun.Operation;

/**
 * Collects and provides statistics of operations executed against the service.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Statistics extends Serializable {
   /**
    * Mark this moment as start of the measurement.
    * No operations should be recorded before this call.
    */
   void begin();

   /**
    * Mark this moment as the end of the measurement.
    * No more operations should be executed after this call.
    */
   void end();

   /**
    * Clean the statistics and start the measurement again.
    */
   void reset();

   /**
    * Register response latency of successful operation.
    * @param responseTime
    * @param operation
    */
   void registerRequest(long responseTime, Operation operation);

   /**
    * Register response latency of failed operation.
    * @param responseTime
    * @param operation
    */
   void registerError(long responseTime, Operation operation);

   /**
    * Create new instance of the same class.
    */
   Statistics newInstance();

   /**
    * Create deep copy of this object
    */
   Statistics copy();

   /**
    * Add the measurements collected into another instance to this instance.
    * @param otherStats Must be of the same class as this instance.
    */
   void merge(Statistics otherStats);

   /**
    * @return Timestamp of the measurement start, in epoch milliseconds.
    */
   long getBegin();

   /**
    * @return Timestamp of the measurement end, in epoch milliseconds.
    */
   long getEnd();

   /**
    * Operation names should be identical on all nodes, as oposed to operations IDs which can differ.
    * @return Map of operations stats keyed by operations names.
    */
   Map<String, OperationStats> getOperationsStats();

   /* Util method, execute only on the same node */

   /**
    * Get particular representation of each operation stats, in array with operation IDs as indices.
    * @param clazz
    * @param <T>
    * @return
    */
   <T> T[] getRepresentations(Class<T> clazz);
}
