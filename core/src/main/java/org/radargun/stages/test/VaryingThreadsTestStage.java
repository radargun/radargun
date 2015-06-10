package org.radargun.stages.test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.Operation;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Test stage with varying number of threads.")
public abstract class VaryingThreadsTestStage extends TestStage {

   @Property(doc = "Minimum number of threads waiting for next operation. Default is 1.")
   private int minWaitingThreads = 1;

   @Property(doc = "Maximum number of executing threads. Default is 1000.")
   private int maxThreads = 1000;

   @Property(doc = "Minimum delay between creating another thread. Default is 20 ms.", converter = TimeConverter.class)
   private long minThreadCreationDelay = 20;

   AtomicInteger waitingThreads = new AtomicInteger();
   AtomicInteger nextThreadIndex = new AtomicInteger();
   AtomicLong lastCreated = new AtomicLong(Long.MIN_VALUE);
   VaryingStressors stressors = new VaryingStressors();

   @Override
   protected OperationSelector createOperationSelector() {
      throw new UnsupportedOperationException("This method needs to be overriden, returning SchedulingOperationSelector");
   }

   @Override
   protected OperationSelector wrapOperationSelector(final OperationSelector operationSelector) {
      return new OperationSelector() {
         @Override
         public void start() {
            operationSelector.start();
         }

         @Override
         public Operation next(Random random) {
            waitingThreads.incrementAndGet();
            Operation operation = null;
            try {
               operation = operationSelector.next(random);
               return operation;
            } finally {
               if (waitingThreads.decrementAndGet() <= minWaitingThreads && !isTerminated() && !isFinished()) {
                  long now = System.currentTimeMillis();
                  long timestamp = lastCreated.get();
                  boolean set = false;
                  while (timestamp + minThreadCreationDelay < now) {
                     if (set = lastCreated.compareAndSet(timestamp, now)) {
                        break;
                     }
                     timestamp = lastCreated.get();
                  }
                  if (set && nextThreadIndex.get() < maxThreads) {
                     addStressor();
                  }
               }
            }
         }
      };
   }

   private void addStressor() {
      Stressor stressor = new Stressor(this, getLogic(), -1, nextThreadIndex.getAndIncrement());
      stressors.add(stressor);
      log.infof("Creating stressor %s", stressor.getName());
      stressor.start();
   }

   @Override
   protected Stressors startStressors() {
      for (int i = 0; i < minWaitingThreads + 1; ++i) {
         addStressor();
      }
      return stressors;
   }

   @Override
   public int getTotalThreads() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int getFirstThreadOn(int slave) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int getNumThreadsOn(int slave) {
      throw new UnsupportedOperationException();
   }

   private class VaryingStressors implements Stressors {
      CopyOnWriteArrayList<Stressor> currentStressors = new CopyOnWriteArrayList<>();

      @Override
      public List<Stressor> getStressors() {
         return currentStressors;
      }

      public void add(Stressor stressor) {
         currentStressors.add(stressor);
      }
   }
}
