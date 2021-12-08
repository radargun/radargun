package org.radargun.stages.test;

import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

import static org.radargun.stages.test.TestStage.STRESSORS_MANAGER;

/**
 * When TestStage has runBackground equals true, we can stop it later.
 * The timeout property can be use to stop it early.
 */
@Stage(doc = "Stop async test stage")
public class StopTestStage extends AbstractDistStage {

   @Property(doc = "Max duration of the test. Default is infinite.", converter = TimeConverter.class)
   public long timeout = 0;

   @Override
   public DistStageAck executeOnWorker() {
      StressorsManager stressorsManagerState = (StressorsManager) workerState.get(STRESSORS_MANAGER);
      if (stressorsManagerState == null) {
         return errorResponse("StressorsManager is null");
      }
      waitForStressorsToFinish(stressorsManagerState, timeout);
      workerState.remove(STRESSORS_MANAGER);
      return successfulResponse();
   }

   public static void waitForStressorsToFinish(StressorsManager manager, long timeout) {
      try {
         if (timeout > 0) {
            long waitTime = getWaitTime(manager.getStartTime(), timeout);
            if (waitTime <= 0) {
               throw new TestTimeoutException();
            } else {
               if (!manager.getFinishCountDown().await(waitTime, TimeUnit.MILLISECONDS)) {
                  throw new TestTimeoutException();
               }
            }
         } else {
            manager.getFinishCountDown().await();
         }
      } catch (InterruptedException e) {
         throw new IllegalStateException("Unexpected interruption", e);
      }
      for (Thread stressorThread : manager.getStressors()) {
         try {
            if (timeout > 0) {
               long waitTime = getWaitTime(manager.getStartTime(), timeout);
               if (waitTime <= 0) throw new TestTimeoutException();
               stressorThread.join(waitTime);
            } else {
               stressorThread.join();
            }
         } catch (InterruptedException e) {
            throw new TestTimeoutException(e);
         }
      }
   }

   private static long getWaitTime(long startTime, long timeout) {
      return startTime + timeout - TimeService.currentTimeMillis();
   }

   public static class TestTimeoutException extends RuntimeException {
      public TestTimeoutException() {
      }

      public TestTimeoutException(Throwable cause) {
         super(cause);
      }
   }
}
