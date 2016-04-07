package org.radargun.stats;

import java.io.Serializable;
import java.util.Set;
import java.util.function.BinaryOperator;

import org.radargun.Operation;
import org.radargun.utils.ReflexiveConverters;

/**
 * Collects and provides statistics of operations executed against the service.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface Statistics extends Serializable {
   BinaryOperator<Statistics> MERGE = (s1, s2) -> s1 == null ? s2 : (s2 == null ? s1 : s1.with(s2));

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
    * This method should be called just before the benchmarked operation. When the operation finishes,
    * either {@link Request#succeeded(Operation)}, {@link Request#failed(Operation)} or {@link Request#discard()}
    * (in case that the request should not be accounted anyhow) must be called. Implementations can
    * track requests and report unfinished (leaked) Requests.
    */
   default Request startRequest() {
      return new Request(this);
   }

   /**
    * Create an object for tracking non-rpc-like operations.
    */
   default Message message() {
      return new Message(this);
   }

   default RequestSet requestSet() {
      return new RequestSet(this);
   }

   /**
    * Should be called only from {@link Request#succeeded(Operation)} and {@link Request#failed(Operation)}.
    *
    * @param request
    * @param operation
    */
   void record(Request request, Operation operation);

   /**
    * Should be called only from {@link Message#record(Operation)}.
    * @param message
    * @param operation
    */
   void record(Message message, Operation operation);

   /**
    * Should be called only from {@link RequestSet#succeeded(Operation)}.
    * @param requestSet
    * @param operation
    */
   void record(RequestSet requestSet, Operation operation);

   default void discard(Request request) {}
   default void discard(Message request) {}
   default void discard(RequestSet request) {}

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
    * Creates new statistics instance with <code>this</code> and the <code>otherStats</code> merged,
    * without mutating this instance.
    *
    * @param otherStats
    * @return
    */
   default Statistics with(Statistics otherStats) {
      Statistics s = copy();
      s.merge(otherStats);
      return s;
   }

   /**
    * @return Timestamp of the measurement start, in epoch milliseconds.
    */
   long getBegin();

   /**
    * @return Timestamp of the measurement end, in epoch milliseconds.
    */
   long getEnd();

   /**
    * @return Names of all operations registered in these statistics.
    */
   Set<String> getOperations();

   /**
    * @param operation
    * @return Instance of operations stats given operation is writing to.
    */
   OperationStats getOperationStats(String operation);

   /**
    * Get particular representation.
    *
    * @param operation
    * @param clazz
    * @param args
    * @return
    */
   <T> T getRepresentation(String operation, Class<T> clazz, Object... args);

   class Converter extends ReflexiveConverters.ObjectConverter {
      public Converter() {
         super(Statistics.class);
      }
   }
}
