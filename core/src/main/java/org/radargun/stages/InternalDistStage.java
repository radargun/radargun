package org.radargun.stages;

import java.util.Collections;
import java.util.Map;

import org.radargun.DistStage;
import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.MainState;
import org.radargun.state.WorkerState;

/**
 * This stage contains copy-paste of some convenience methods from {@link org.radargun.stages.AbstractDistStage}
 * - we have decided to simplify the hierarchy and not include two abstract dist stages.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Base for internal stages", internal = true)
public abstract class InternalDistStage extends AbstractStage implements DistStage {
   protected Log log = LogFactory.getLog(getClass());
   /**
    * This field is filled in only on main node, on worker it is set to null
    */
   protected MainState mainState;
   /**
    * This field is filled in only on worker node, on main it is set to null
    */
   protected WorkerState workerState;

   @Override
   public void initOnMain(MainState mainState) {
      this.mainState = mainState;
   }

   @Override
   public Map<String, Object> createMainData() {
      return Collections.EMPTY_MAP;
   }

   @Override
   public void initOnWorker(WorkerState workerState) {
      this.workerState = workerState;
   }

   @Override
   public boolean shouldExecute() {
      return true;
   }

   protected DistStageAck errorResponse(String message) {
      log.error(message);
      return new DistStageAck(workerState).error(message, null);
   }

   protected DistStageAck errorResponse(String message, Exception e) {
      log.error(message, e);
      return new DistStageAck(workerState).error(message, e);
   }

   protected DistStageAck successfulResponse() {
      return new DistStageAck(workerState);
   }
}
