package org.radargun.stages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.radargun.DistStageAck;
import org.radargun.ServiceContext;
import org.radargun.ServiceHelper;
import org.radargun.StageResult;
import org.radargun.config.Stage;
import org.radargun.state.SlaveState;

@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted after the ServiceStartStage.")
public final class AfterServiceStartStage extends AbstractDistStage {

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      Map<String, Object> serviceProperties = new HashMap<>();
      if (!result.isError()) {
         for (ServiceContextAck ack : instancesOf(acks, ServiceContextAck.class)) {
            ServiceContext context = ack.getServiceContext();
            for (Map.Entry<String, Object> e : context.getProperties().entrySet()) {
               //store entries from slaves in the format: ${group.slaveIndex.key}
               String key = e.getKey().startsWith(context.getPrefix()) ?
                  e.getKey() : context.getPrefix() + "." + e.getKey();
               serviceProperties.put(key, e.getValue());
            }
         }
         log.trace("ServiceContext properties on master: " + serviceProperties);
         masterState.put(ServiceContext.class.getName(), serviceProperties);
      }
      return result;
   }

   //Send data from Slave to Master
   public DistStageAck executeOnSlave() {
      return new ServiceContextAck(slaveState);
   }

   public static class ServiceContextAck extends DistStageAck {
      private ServiceContext serviceContext;

      private ServiceContextAck(SlaveState slaveState) {
         super(slaveState);
         serviceContext = ServiceHelper.getContext();
      }

      public ServiceContext getServiceContext() {
         return serviceContext;
      }
   }
}
