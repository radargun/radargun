package org.cachebench.fwk.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.fwk.DistStageAck;

import java.util.List;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public class DummyStage extends AbstractDistStage{

   private static Log log = LogFactory.getLog(DummyStage.class);

   private String name;

   public DummyStage(String name) {
      this.name = name;
   }

   public DistStageAck executeOnNode() {
      log.trace("Stage: " + name);
      return newDefaultStageAck();
   }

   public boolean processAckOnServer(List<DistStageAck> acks) {
      log.trace("Stage " + name + "was acknowledged: " + acks);
      return true;
   }


   @Override
   public String toString() {
      return "DummyStage{" +
            "name='" + name + '\'' +
            "} " + super.toString();
   }
}
