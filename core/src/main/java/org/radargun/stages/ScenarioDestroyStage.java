package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stages.lifecycle.LifecycleHelper;
import org.radargun.state.ServiceListener;
import org.radargun.utils.Utils;

/**
 * This stage is the last stage that gets {@link org.radargun.traits.Trait Traits} injected
 * and which is ran in the {@link org.radargun.Slave.ScenarioRunner} thread.
 * It should destroy the service - afterwards, no more references to service should be held.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted after the last stage in each scenario.")
public class ScenarioDestroyStage extends AbstractDistStage {
   @Override
   public DistStageAck executeOnSlave() {
      log.info("Scenario finished, destroying...");
      log.info("Memory before cleanup: \n" + Utils.getMemoryInfo());
      try {
         if (lifecycle != null && lifecycle.isRunning()) {
            LifecycleHelper.stop(slaveState, true, false);
            log.info("Service successfully stopped.");
         } else {
            log.info("No service deployed on this slave, nothing to do.");
         }
      } catch (Exception e) {
         return errorResponse("Problems shutting down the slave", e);
      } finally {
         log.trace("Calling destroy hooks");
         for (ServiceListener listener : slaveState.getServiceListeners()) {
            listener.serviceDestroyed();
         }
         //reset the class loader to SystemClassLoader
         Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
         slaveState.reset();
      }
      return successfulResponse();
   }
}
