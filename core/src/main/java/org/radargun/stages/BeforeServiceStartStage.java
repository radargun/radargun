package org.radargun.stages;

import java.util.Collections;
import java.util.Map;
import org.radargun.DistStageAck;
import org.radargun.ServiceContext;
import org.radargun.config.Stage;

@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted before the ServiceStartStage.")
public final class BeforeServiceStartStage extends AbstractDistStage {

   public DistStageAck executeOnSlave() {
      return successfulResponse();
   }

   //Send this data from Master to Slaves
   @Override
   public Map<String, Object> createMasterData() {
      Map<String, Object> serviceProperties =
         (Map<String, Object>) masterState.get(ServiceContext.class.getName());
      if (serviceProperties != null) {
         return Collections.singletonMap(ServiceContext.class.getName(), serviceProperties);
      } else {
         return Collections.EMPTY_MAP;
      }
   }
}
