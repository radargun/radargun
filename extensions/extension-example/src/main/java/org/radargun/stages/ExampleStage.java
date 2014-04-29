package org.radargun.stages;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;

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
      DefaultDistStageAck ack = newDefaultStageAck();
      ack.setPayload(String.format("Slave %d said: %s", slaveState.getSlaveIndex(), foo));
      return ack;
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean successful = super.processAckOnMaster(acks);
      if (successful) {
         for (DistStageAck ack : acks) {
            log.info(String.format("Slave %d reports: %s",
                  ack.getSlaveIndex(), ((DefaultDistStageAck) ack).getPayload()));
         }
      }
      return successful;
   }
}
