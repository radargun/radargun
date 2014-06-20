package org.radargun.stages;

import static org.radargun.utils.Utils.cast;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.TimeConverter;
import org.radargun.state.SlaveState;

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
      log.info(String.format("Slave %d says: %s", slaveState.getSlaveIndex(), foo));
      return new ExampleAck(slaveState, String.format("Slave %d said: %s", slaveState.getSlaveIndex(), foo));
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean successful = super.processAckOnMaster(acks);
      if (successful) {
         for (ExampleAck ack : cast(acks, ExampleAck.class)) {
            log.info(String.format("Slave %d reports: %s", ack.getSlaveIndex(), ack.getExampleMessage()));
         }
      }
      return successful;
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
