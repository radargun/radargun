package org.radargun.stages;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Use for setting certain value")
public class DefineStage extends AbstractDistStage {
   @Property(doc = "Name of the variable that should be set.", optional = false)
   public String var;

   @Property(doc = "Value of the variable.", optional = false)
   public String value;

   @Override
   public DistStageAck executeOnSlave() {
      slaveState.put(var, value);
      return successfulResponse();
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (!result.isError()) {
         masterState.put(var, value);
      }
      return result;
   }
}
