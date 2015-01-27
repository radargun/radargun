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
   @Property(doc = "Name of the background operations. By default, all instances are checked.")
   protected String name = null;

   @Property(doc = "Do not write additional operations until all operations are confirmed. Default is false.")
   private boolean waitUntilChecked = false;

   @Property(doc = "Resume writers after we have stopped them in order to let checkers check everything. Default is false.")
   private boolean resumeAfterChecked = false;

   @Override
   public DistStageAck executeOnSlave() {
      if (name != null) {
         BackgroundOpsManager manager = BackgroundOpsManager.getInstance(slaveState, name);
         if (manager == null) {
            return errorResponse("Manager '" + name + "' does not exist");
         } else {
            DistStageAck response = checkManager(manager);
            if (response != null) return response;
         }
      } else {
         for (BackgroundOpsManager manager : BackgroundOpsManager.getAllInstances(slaveState)) {
            DistStageAck response = checkManager(manager);
            if (response != null) return response;
         }
      }
      return successfulResponse();
   }

   private DistStageAck checkManager(BackgroundOpsManager manager) {
      String error = manager.getError();
      if (error != null) {
         return errorResponse("Background stressors " + manager.getName() + ": " + error);
      }
      if (waitUntilChecked && resumeAfterChecked) {
         return errorResponse("Cannot both wait and resume in the same stage; other node may have not finished checking.");
      }
      if (!isServiceRunning()) {
         return successfulResponse();
      }
      if (waitUntilChecked) {
         error = manager.waitUntilChecked();
         if (error != null) {
            return errorResponse(error);
         }
      } else if (resumeAfterChecked) {
         manager.resumeAfterChecked();
      }
      return null;
   }
}
