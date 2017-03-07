package org.radargun.stages.test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * A class that holds additional information about stressors and makes is possible to
 * pass stressors together with this information between stages. An arbitrary stage can wait
 * for the stressors to finish and track their execution time.
 *
 * @author Martin Gencur
 */
public class StressorsManager {

   private CountDownLatch finishCountDown;
   private long startTime;
   private List<Stressor> stressors;

   public StressorsManager(List<Stressor> stressors, long startTime, CountDownLatch finishCountDown) {
      this.stressors = stressors;
      this.startTime = startTime;
      this.finishCountDown = finishCountDown;
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
}
