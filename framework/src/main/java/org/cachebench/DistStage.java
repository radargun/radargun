package org.cachebench;

import org.cachebench.state.SlaveState;
import org.cachebench.state.MasterState;

import java.io.Serializable;
import java.util.List;

/**
 * Defines an stage that will be run on both master and slaves.
 *
 * @author Mircea.Markus@jboss.com
 */
public interface DistStage extends Stage, Serializable {

   /**
    * Called on master. master state should not be passed to the slaves.
    */
   public void initOnMaster(MasterState masterState, int totalSlavesCount);

   /**
    * Before sending the state to the slave, this will be called to set salve's index.
    */
   public void setSlaveIndex(int slaveIndex);

   /**
    * Aftert unmarshalling on the slave, this method will be called to init the stage with slave's state.
    */
   public void initOnSlave(SlaveState slaveState);

   public int getActiveSlaveCount();

   public int getTotalSlavesCount();

   public void setActiveSlavesCount(int activeSlaves);

   /**
    * Do whatever on the slave. This will only be called after {@link #initOnSlave(org.cachebench.state.SlaveState)} is called.
    * @return an response that will be serialized and send back to the master.
    */
   DistStageAck executeOnSlave();

   /**
    * After all slaves replied through {@link #executeOnSlave()}, this method will be called on the master.
    * @return returning false will cause the benchmark to stop.
    */
   boolean processAckOnMaster(List<DistStageAck> acks);

   public DistStage clone();

   public boolean isRunOnAllSlaves();
}
