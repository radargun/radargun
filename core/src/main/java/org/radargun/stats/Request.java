package org.radargun.stats;

import java.util.function.Predicate;
import java.util.function.Supplier;

import org.radargun.Operation;
import org.radargun.utils.TimeService;

/**
 * Tracks time for RPC-like operations, possibly asynchronous (TODO).
 * @see Message for tracking messages with different origin and destination.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Request {
   private final Statistics statistics;
   private final long requestStartTime;
//   This is for async:
//   private long requestCompleteTime;
//   private long responseStartedTime;
   private long responseCompleteTime = Long.MIN_VALUE;
   private boolean successful = true;
   private boolean discarded = false;

   public Request(Statistics statistics) {
      this.statistics = statistics;
      this.requestStartTime = TimeService.nanoTime();
   }

   public void exec(Operation operation, Runnable runnable) {
      try {
         runnable.run();
         succeeded(operation);
      } catch (Throwable t) {
         failed(operation);
         throw t;
      }
   }

   public <T> T exec(Operation operation, Supplier<T> supplier) {
      try {
         T value = supplier.get();
         succeeded(operation);
         return value;
      } catch (Throwable t) {
         failed(operation);
         throw t;
      }
   }

   public <T> T exec(Operation operation, Supplier<T> supplier, Predicate<T> predicate) {
      T value;
      try {
         value = supplier.get();
      } catch (Throwable t) {
         failed(operation);
         throw t;
      }
      responseCompleteTime = TimeService.nanoTime();
      try {
         successful = predicate.test(value);
      } catch (Throwable t) {
         successful = false;
         throw t;
      } finally {
         statistics.record(this, operation);
      }
      return value;
   }

//   This is for async:
//   public void requestCompleted() {}
//   public void requestFailed() {}
//   public void responseStarted() {}

   public void succeeded(Operation operation) {
      this.responseCompleteTime = TimeService.nanoTime();
      statistics.record(this, operation);
   }

   public void failed(Operation operation) {
      this.responseCompleteTime = TimeService.nanoTime();
      this.successful = false;
      statistics.record(this, operation);
   }

   public void discard() {
      this.discarded = true;
      statistics.discard(this);
   }

   public boolean isSuccessful() {
      return successful;
   }

   public boolean isFinished() {
      return discarded || responseCompleteTime > Long.MIN_VALUE;
   }

   public long getRequestStartTime() {
      return requestStartTime;
   }

   public long getResponseCompleteTime() {
      return responseCompleteTime;
   }

   /**
    * @return Total duration in nanoseconds.
    */
   public long duration() {
      return responseCompleteTime - requestStartTime;
   }
}
