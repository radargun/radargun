package org.radargun.stages;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.WorkerState;
import org.radargun.utils.TimeConverter;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Example stage in different module")
public class ExampleStage extends AbstractDistStage {

   @Property(doc = "Example property that must be set", optional = false)
   private String foo;

   @Property(doc = "Example property delaying the stage executions. Default is 5 seconds.", converter = TimeConverter.class)
   private long delay = 5000;

   @Override
   public DistStageAck executeOnWorker() {
      try {
         Thread.sleep(delay);
      } catch (InterruptedException e) {
         log.warn("Stage was interrupted!", e);
      }
      log.infof("Worker %d says: %s", workerState.getWorkerIndex(), foo);
      return new ExampleAck(workerState, String.format("Worker %d said: %s", workerState.getWorkerIndex(), foo));
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMain(acks);
      if (!result.isError()) {
         for (ExampleAck ack : instancesOf(acks, ExampleAck.class)) {
            log.infof("Worker %d reports: %s", ack.getWorkerIndex(), ack.getExampleMessage());
         }
      }
      return result;
   }

   private static class ExampleAck extends DistStageAck {
      private String exampleMessage;

      public ExampleAck(WorkerState workerState, String exampleMessage) {
         super(workerState);
         this.exampleMessage = exampleMessage;
      }

      public String getExampleMessage() {
         return exampleMessage;
      }
   }
}
