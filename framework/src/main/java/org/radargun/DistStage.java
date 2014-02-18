package org.radargun;

import java.io.Serializable;
import java.util.List;

import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;

/**
 * Defines an stage that will be run on both master and slaves.
 *
 * @author Mircea.Markus@jboss.com
 */
public interface DistStage extends Stage, Serializable {

   void initOnMaster(MasterState masterState);
   void initOnSlave(SlaveState slaveState);

   /**
    * Do whatever on the slave. This will only be called after {@link #initOnSlave(org.radargun.state.SlaveState)} is called.
    * @return an response that will be serialized and send back to the master.
    */
   DistStageAck executeOnSlave();

   /**
    * After all slaves replied through {@link #executeOnSlave()}, this method will be called on the master.
    * @return returning false will cause the benchmark to stop.
    */
   boolean processAckOnMaster(List<DistStageAck> acks);

   public boolean isRunOnAllSlaves();

   public boolean isExitBenchmarkOnSlaveFailure();
}
