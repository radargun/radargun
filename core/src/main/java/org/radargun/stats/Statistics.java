package org.radargun.stats;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.utils.ReflexiveConverters;

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
    * Register response latency in nanoseconds of successful operation.
    * @param responseTime
    * @param operation
    */
   void registerRequest(long responseTime, Operation operation);

   /**
    * Register response latency in nanoseconds of failed operation.
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
    * Creates a logical group of operations identified by given group name. Should be used on per-stage basis.
    *
    * @param name Name of the group
    * @param operations Operations to include in logical group
    */
   void registerOperationsGroup(String name, Set<Operation> operations);

   /**
    * @return Group name for supplied operation. Returns null if group with given operation is not registered.
    */
   String getOperationsGroup(Operation operation);

   /**
    * @return Map all registered groups and their corresponding operations
    */
   Map<String, Set<Operation>> getGroupOperationsMap();

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

   /**
    * @return Merged operation stats for registered groups. Calculation should be based on operations registered with this group.
    */
   Map<String, OperationStats> getOperationStatsForGroups();

   /* Util method, execute only on the same node */

   /**
    * Get particular representation of each operation stats, in array with operation IDs as indices.
    * @param clazz
    * @param args
    * @return
    */
   <T> T[] getRepresentations(Class<T> clazz, Object... args);

   public static class Converter extends ReflexiveConverters.ObjectConverter {
      public Converter() {
         super(Statistics.class);
      }
   }
}
