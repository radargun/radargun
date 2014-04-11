package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted before the beginning of scenario.")
public class ScenarioInitStage extends AbstractDistStage {
   static final String INITIAL_FREE_MEMORY = "INITIAL_FREE_MEMORY";

   @Override
   public DistStageAck executeOnSlave() {
      slaveState.put(INITIAL_FREE_MEMORY, Runtime.getRuntime().freeMemory());
      return successfulResponse();
   }
}
