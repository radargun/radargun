package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stressors.BackgroundOpsManager;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage that checks the progress in background stressors and fails if something went wrong.")
public class CheckBackgroundStressorsStage extends AbstractDistStage {
   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (slaves != null && !slaves.contains(slaveIndex)) {
         return ack;
      }
      BackgroundOpsManager manager = BackgroundOpsManager.getInstance(slaveState);
      if (manager != null) {
         String error = manager.getError();
         if (error != null) {
            log.error(error);
            ack.setError(true);
            ack.setErrorMessage(error);
         }
      }
      return ack;
   }
}
