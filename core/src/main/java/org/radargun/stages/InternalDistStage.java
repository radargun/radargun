package org.radargun.stages;

import java.util.Collections;
import java.util.Map;

import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;

/**
 * This stage contains copy-paste of some convenience methods from {@link org.radargun.stages.AbstractDistStage}
 * - we have decided to simplify the hierarchy and not include two abstract dist stages.
 */
@Stage(doc = "Base for internal stages", internal = true)
public abstract class InternalDistStage extends AbstractStage implements DistStage {
   protected Log log = LogFactory.getLog(getClass());
   /**
    * This field is filled in only on master node, on slave it is set to null
    */
   protected MasterState masterState;
   /**
    * This field is filled in only on slave node, on master it is set to null
    */
   protected SlaveState slaveState;

   @Override
   public void initOnMaster(MasterState masterState) {
      this.masterState = masterState;
   }

   @Override
   public Map<String, Object> createMasterData() {
      return Collections.EMPTY_MAP;
   }

   @Override
   public void initOnSlave(SlaveState slaveState) {
      this.slaveState = slaveState;
   }

   @Override
   public boolean shouldExecute() {
      return true;
   }

   protected DistStageAck errorResponse(String message) {
      log.error(message);
      return new DistStageAck(slaveState).error(message, null);
   }

   protected DistStageAck errorResponse(String message, Exception e) {
      log.error(message, e);
      return new DistStageAck(slaveState).error(message, e);
   }

   protected DistStageAck successfulResponse() {
      return new DistStageAck(slaveState);
   }
}
