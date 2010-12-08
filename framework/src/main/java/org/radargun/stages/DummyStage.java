package org.radargun.stages;

import org.radargun.DistStageAck;
import org.radargun.state.MasterState;

import java.util.List;

/**
 * Stge used for tests.
 *
 * @author Mircea.Markus@jboss.com
 */
public class DummyStage extends AbstractDistStage{

   private String name;

   public DummyStage(String name) {
      this.name = name;
   }

   public DistStageAck executeOnSlave() {
      log.trace("Stage: " + name);
      return newDefaultStageAck();
   }

   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      log.trace("Stage " + name + "was acknowledged: " + acks);
      return true;
   }


   @Override
   public String toString() {
      return "DummyStage {" +
            "name='" + name + '\'' +
            ", " + super.toString();
   }
}
