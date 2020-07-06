package org.radargun.stages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.radargun.DistStageAck;
import org.radargun.ServiceContext;
import org.radargun.ServiceHelper;
import org.radargun.StageResult;
import org.radargun.config.Stage;
import org.radargun.state.WorkerState;

@Stage(internal = true, doc = "DO NOT USE DIRECTLY. This stage is automatically inserted after the ServiceStartStage.")
public final class AfterServiceStartStage extends AbstractDistStage {

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMain(acks);
      Map<String, Object> serviceProperties = new HashMap<>();
      if (!result.isError()) {
         for (ServiceContextAck ack : instancesOf(acks, ServiceContextAck.class)) {
            ServiceContext context = ack.getServiceContext();
            for (Map.Entry<String, Object> e : context.getProperties().entrySet()) {
               //store entries from workers in the format: ${group.workerIndex.key}
               String key = e.getKey().startsWith(context.getPrefix()) ?
                  e.getKey() : context.getPrefix() + "." + e.getKey();
               serviceProperties.put(key, e.getValue());
            }
         }
         log.trace("ServiceContext properties on main: " + serviceProperties);
         mainState.put(ServiceContext.class.getName(), serviceProperties);
      }
      return result;
   }

   //Send data from Worker to Main
   public DistStageAck executeOnWorker() {
      return new ServiceContextAck(workerState);
   }

   public static class ServiceContextAck extends DistStageAck {
      private ServiceContext serviceContext;

      private ServiceContextAck(WorkerState workerState) {
         super(workerState);
         serviceContext = ServiceHelper.getContext();
      }

      public ServiceContext getServiceContext() {
         return serviceContext;
      }
   }
}
