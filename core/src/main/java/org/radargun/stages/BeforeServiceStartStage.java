package org.radargun.stages;

import java.util.Collections;
import java.util.Map;
import org.radargun.DistStageAck;
import org.radargun.ServiceContext;
import org.radargun.config.Stage;

@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted before the ServiceStartStage.")
public final class BeforeServiceStartStage extends AbstractDistStage {

   public DistStageAck executeOnWorker() {
      return successfulResponse();
   }

   //Send this data from Main to Workers
   @Override
   public Map<String, Object> createMainData() {
      Map<String, Object> serviceProperties =
         (Map<String, Object>) mainState.get(ServiceContext.class.getName());
      if (serviceProperties != null) {
         return Collections.singletonMap(ServiceContext.class.getName(), serviceProperties);
      } else {
         return Collections.EMPTY_MAP;
      }
   }
}
