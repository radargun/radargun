package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

import org.radargun.Operation;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Projections;

/**
 * Based on provided frequency, returns matching invocation from {@link #next()} or blocks the thread calling it.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SchedulingOperationSelector implements OperationSelector {
   private final static Log log = LogFactory.getLog(SchedulingOperationSelector.class);
   private final static boolean trace = log.isTraceEnabled();
   private final int[] frequencies;
   private final int[] intervals;
   private final Operation[] operations;
   private final int numOperations;
   private final AtomicLongArray lastSlots;
   private final AtomicIntegerArray todoInvocations;
   private volatile int offset;

   /**
    * @param operations Returned invocations
    * @param frequencies Number of operations per slot
    * @param intervals Size of slot, in milliseconds
    */
   public SchedulingOperationSelector(Operation[] operations, int[] frequencies, int[] intervals) {
      if (operations.length != frequencies.length) throw new IllegalArgumentException();
      this.operations = operations;
      this.frequencies = frequencies;
      this.intervals = intervals;
      numOperations = operations.length;
      lastSlots = new AtomicLongArray(numOperations);
      todoInvocations = new AtomicIntegerArray(numOperations);
   }

   /**
    * @return
    * @throws InterruptedException
    */
   public Operation next() throws InterruptedException {
      WAIT_LOOP: for (;;) {
         long now = System.currentTimeMillis();
         int myOffset = offset;
         INVOCATIONS_LOOP: for (int i = 0; i < numOperations; ++i) {
            int operationIndex = (i + myOffset) % numOperations;

            int myInterval = intervals[operationIndex];
            long currentInterval = now / myInterval;

            long lastInterval;
            boolean set = false;
            do {
               lastInterval = lastSlots.get(operationIndex);
            } while (currentInterval > lastInterval && !(set = lastSlots.compareAndSet(operationIndex, lastInterval, currentInterval)));
            if (set) {
               int frequency = frequencies[operationIndex];
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

   @Override
   public void start() {
   }

   @Override
   public Operation next(Random ignored) {
      try {
         return next();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      }
   }

   public static class Builder {
      private List<Operation> operations = new ArrayList<>();
      private List<Integer> frequencies = new ArrayList<>();
      private List<Integer> slotSizes = new ArrayList<>();

      public Builder add(Operation operation, int frequency, int slotSize) {
         if (operation == null) {
            throw new IllegalArgumentException();
         }
         if (frequency <= 0) {
            return this;
         }
         if (slotSize <= 0) {
            throw new IllegalArgumentException(operation.name + ": slotSize " + String.valueOf(slotSize));
         }
         operations.add(operation);
         frequencies.add(frequency);
         slotSizes.add(slotSize);
         return this;
      }

      public SchedulingOperationSelector build() {
         if (operations.isEmpty()) throw new IllegalStateException("No operations set!");
         return new SchedulingOperationSelector(operations.toArray(new Operation[operations.size()]),
               Projections.toIntArray(frequencies), Projections.toIntArray(slotSizes));
      }
   }
}
