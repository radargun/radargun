package org.radargun.stages.cache.background;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage that checks the progress in background stressors and fails if something went wrong.")
public class BackgroundStressorsCheckStage extends AbstractDistStage {

   @Property(doc = "Do not write additional operations until all operations are confirmed. Default is false.")
   private boolean waitUntilChecked = false;

   @Property(doc = "Resume writers after we have stopped them in order to let checkers check everything. Default is false.")
   private boolean resumeAfterChecked = false;

   @Override
   public DistStageAck executeOnSlave() {
      if (!shouldExecute()) {
         return successfulResponse();
      }
      BackgroundOpsManager manager = BackgroundOpsManager.getInstance(slaveState);
      if (manager != null) {
         String error = manager.getError();
         if (error != null) {
            return errorResponse(error);
         }
         if (waitUntilChecked && resumeAfterChecked) {
            return errorResponse("Cannot both wait and resume in the same stage; other node may have not finished checking.");
         }
         if (waitUntilChecked) {
            error = manager.waitUntilChecked();
            if (error != null) {
               return errorResponse(error);
            }
         } else if (resumeAfterChecked) {
            manager.resumeAfterChecked();
         }
      }
      return successfulResponse();
   }
}
