package org.radargun.stages.test.async;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;

import org.radargun.utils.TimeService;

/**
 * Based on provided frequency, returns matching invocation from {@link #next()} or blocks the thread calling it.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SchedulingSelector<T> {
   private static final AtomicIntegerFieldUpdater offsetUpdater = AtomicIntegerFieldUpdater.newUpdater(SchedulingSelector.class, "offset");
   private static final long LOW_WORD = 0xFFFFFFFFL;
   private final int[] invocations;
   private final long[] intervals;
   private final T[] operations;
   // Higher word of count contains the truncated lowest bits of the actual interval number, lower word contains
   // number of operations executed in this interval.
   private final AtomicLongArray counts;
   private volatile int offset = 0;

   /**
    * @param operations Returned invocations
    * @param invocations Number of operations per interval
    * @param intervals Size of slot, in milliseconds
    */
   public SchedulingSelector(T[] operations, int[] invocations, long[] intervals) {
      if (operations.length != invocations.length) throw new IllegalArgumentException();
      this.operations = operations;
      this.invocations = invocations;
      this.intervals = intervals;
      counts = new AtomicLongArray(operations.length);
      long now = TimeService.nanoTime();
      for (int i = 0; i < operations.length; ++i) {
         counts.set(i, now << 32);
      }
   }

   public T next() throws InterruptedException {
      WAIT_LOOP:
      do {
         // While nanoTime() is more expensive than currentTimeMillis() but it's monotonic at least
         long now = TimeService.nanoTime();
         // Just to spread the iteration start more evenly; we don't want all operations of the same type
         // to be executed in parallel
         int myOffset = offset;
         offsetUpdater.lazySet(this, myOffset + 1);

         long sleepTime = Long.MAX_VALUE;
         INVOCATIONS_LOOP:
         for (int i = 0; i < operations.length; ++i) {
            int operationIndex = (i + myOffset) % operations.length;

            long myInterval = intervals[operationIndex];
            long currentInterval = LOW_WORD & (now / (ns2ms(myInterval)));

            long count, nextCount;
            do {
               count = counts.get(operationIndex);
               long lastInterval = count >>> 32;
               if (lastInterval == currentInterval) {
                  if ((count & LOW_WORD) < invocations[operationIndex]) {
                     nextCount = count + 1;
                  } else {
                     sleepTime = Math.min(sleepTime, ns2ms(myInterval) - now % ns2ms(myInterval));
                     continue INVOCATIONS_LOOP;
                  }
               } else {
                  nextCount = (currentInterval << 32) + 1;
               }
            } while (!counts.compareAndSet(operationIndex, count, nextCount));
            return operations[operationIndex];
         }
         LockSupport.parkNanos(sleepTime);
      } while (!Thread.currentThread().isInterrupted());
      throw new InterruptedException();
   }

   private long ns2ms(long ms) {
      return 1_000_000 * ms;
   }

   public static class Builder<T> {
      private final Class<T> clazz;
      private List<T> operations = new ArrayList<>();
      private List<Integer> invocations = new ArrayList<>();
      private List<Long> intervals = new ArrayList<>();

      public Builder(Class<T> clazz) {
         this.clazz = clazz;
      }

      public Builder<T> add(T operation, int invocations, long interval) {
         if (operation == null) {
            throw new IllegalArgumentException();
         }
         if (invocations <= 0) {
            return this;
         }
         if (interval <= 0) {
            throw new IllegalArgumentException(operation + ": interval " + String.valueOf(interval));
         }
         operations.add(operation);
         this.invocations.add(invocations);
         intervals.add(interval);
         return this;
      }

      public SchedulingSelector<T> build() {
         if (operations.isEmpty()) throw new IllegalStateException("No operations set!");
         return new SchedulingSelector(operations.toArray((T[]) Array.newInstance(clazz, operations.size())),
               invocations.stream().mapToInt(i -> i.intValue()).toArray(),
               intervals.stream().mapToLong(l -> l.longValue()).toArray());
      }
   }
}
