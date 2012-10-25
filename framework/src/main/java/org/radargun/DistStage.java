package org.radargun;

import org.radargun.state.SlaveState;
import org.radargun.state.MasterState;

import java.io.Serializable;
import java.util.List;

/**
 * Defines an stage that will be run on both master and slaves.
 *
 * @author Mircea.Markus@jboss.com
 */
public interface DistStage extends Stage, Serializable {

   /**
    * After un-marshalling on the slave, this method will be called to setUp the stage with slave's state.
    */
   void initOnSlave(SlaveState slaveState);

   int getActiveSlaveCount();

   void setActiveSlavesCount(int activeSlaves);

   /**
    * Do whatever on the slave. This will only be called after {@link #initOnSlave(org.radargun.state.SlaveState)} is called.
    * @return an response that will be serialized and send back to the master.
    */
   DistStageAck executeOnSlave();

   /**
    * Called on master. Master state should not be passed to the slaves.
    */
   void initOnMaster(MasterState masterState, int slaveIndex);

   /**
    * After all slaves replied through {@link #executeOnSlave()}, this method will be called on the master.
    * @return returning false will cause the benchmark to stop.
    */
   boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState);

   DistStage clone();

   boolean isRunOnAllSlaves();

   void setRunOnAllSlaves(boolean runOnAllSlaves);

   boolean isExitBenchmarkOnSlaveFailure();

   void setExitBenchmarkOnSlaveFailure(boolean exitOnFailure);
}
