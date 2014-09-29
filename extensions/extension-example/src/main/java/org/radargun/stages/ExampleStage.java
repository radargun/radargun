package org.radargun.stages;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.SlaveState;
import org.radargun.utils.Projections;
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
   public DistStageAck executeOnSlave() {
      try {
         Thread.sleep(delay);
      } catch (InterruptedException e) {
         log.warn("Stage was interrupted!", e);
      }
      log.infof("Slave %d says: %s", slaveState.getSlaveIndex(), foo);
      return new ExampleAck(slaveState, String.format("Slave %d said: %s", slaveState.getSlaveIndex(), foo));
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (!result.isError()) {
         for (ExampleAck ack : Projections.instancesOf(acks, ExampleAck.class)) {
            log.infof("Slave %d reports: %s", ack.getSlaveIndex(), ack.getExampleMessage());
         }
      }
      return result;
   }

   private static class ExampleAck extends DistStageAck {
      private String exampleMessage;

      public ExampleAck(SlaveState slaveState, String exampleMessage) {
         super(slaveState);
         this.exampleMessage = exampleMessage;
      }

      public String getExampleMessage() {
         return exampleMessage;
      }
   }
}
