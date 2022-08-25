package org.radargun.stages.test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class that holds additional information about stressors and makes is possible to
 * pass stressors together with this information between stages. An arbitrary stage can wait
 * for the stressors to finish and track their execution time.
 *
 * @author Martin Gencur
 */
public class StressorsManager {

   private final CountDownLatch finishCountDown;
   private final long startTime;
   private final List<Stressor> stressors;
   private final AtomicBoolean continueRunning;

   public StressorsManager(List<Stressor> stressors, long startTime, CountDownLatch finishCountDown, AtomicBoolean continueRunning) {
      this.stressors = stressors;
      this.startTime = startTime;
      this.finishCountDown = finishCountDown;
      this.continueRunning = continueRunning;
   }

   public long getStartTime() {
      return startTime;
   }

   public List<Stressor> getStressors() {
      return stressors;
   }

   public CountDownLatch getFinishCountDown() {
      return finishCountDown;
   }

   public void forceStop() {
      this.continueRunning.set(false);
   }

   public boolean wasForceStopped() {
      return !this.continueRunning.get();
   }
}
