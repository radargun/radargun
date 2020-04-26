package org.radargun.stages;

import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;

@Stage(doc = "Allows to system out messages for more readable logs.")
public class EchoStage extends AbstractMasterStage {
   @Property(optional = false, doc = "Message to be printed.")
   private String message;


   @Override
   public StageResult execute() {
      log.info(message);

      return StageResult.SUCCESS;
   }
}
