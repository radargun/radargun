package org.radargun.stages.test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

import org.radargun.utils.TimeService;

/**
 * Based on provided frequency, returns matching invocation from {@link #next()} or blocks the thread calling it.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SchedulingSelector<T> {
   private final int[] invocations;
   private final long[] intervals;
   private final T[] operations;
   private final AtomicLongArray lastIntervals;
   private final AtomicIntegerArray todoInvocations;
   private volatile int offset;

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
      lastIntervals = new AtomicLongArray(operations.length);
      todoInvocations = new AtomicIntegerArray(operations.length);
      for (int i = 0; i < operations.length; ++i) {
         lastIntervals.set(i, Long.MIN_VALUE);
      }
   }

   /**
    * @return
    * @throws InterruptedException
    */
   public T next() throws InterruptedException {
      WAIT_LOOP:
      for (; ; ) {
         long now = TimeService.currentTimeMillis();
         int myOffset = offset;
         INVOCATIONS_LOOP:
         for (int i = 0; i < operations.length; ++i) {
            int operationIndex = (i + myOffset) % operations.length;

            long myInterval = intervals[operationIndex];
            long currentInterval = now / myInterval;

            long lastInterval;
            boolean hasSetLastInterval = false;
            do {
               lastInterval = lastIntervals.get(operationIndex);
            }
            while (currentInterval > lastInterval && !(hasSetLastInterval = lastIntervals.compareAndSet(operationIndex, lastInterval, currentInterval)));
            if (hasSetLastInterval) {
               int frequency = invocations[operationIndex];
               // we ignore the requests that should have been executed in previous slots;
               // if the slot is too small
               // -1 for immediatelly executed request
               todoInvocations.set(operationIndex, frequency - 1);
               synchronized (this) {
                  this.notifyAll();
               }
            } else {
               int todos;
               do {
                  todos = todoInvocations.get(operationIndex);
                  if (todos <= 0) continue INVOCATIONS_LOOP;
               } while (!todoInvocations.compareAndSet(operationIndex, todos, todos - 1));
            }

            offset++;
            return operations[operationIndex];
         }
         synchronized (this) {
            // there is a race that thread A sets last timestamp but B checks
            // the other branch before A updates todos. Then B goes to sleep after
            // not finding any todos, but after A calling the notify. Therefore,
            // we check once more here, while synchronized
            for (int i = 0; i < todoInvocations.length(); ++i) {
               if (todoInvocations.get(i) != 0) {
                  continue WAIT_LOOP;
               }
            }
            this.wait(1);
         }
      }
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
