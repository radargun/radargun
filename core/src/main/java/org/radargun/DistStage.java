package org.radargun;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.radargun.state.MasterState;
import org.radargun.state.SlaveState;

/**
 * Defines an stage that will be run on both master and slaves.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public interface DistStage extends Stage, Serializable {

   /**
    * Initialize the stage on master node.
    * @param masterState
    */
   void initOnMaster(MasterState masterState);

   /**
    * Creates custom data that should be passed to the slave and inserted into its state before
    * the stage is resolved. Those values can be used both for property resolution (the values
    * can be then stringified) or retrieved through {@link SlaveState#get(String)} later during
    * {@link #executeOnSlave()}.
    * @return Map of variable names to objects.
    */
   Map<String, Object> createMasterData();

   /**
    * Initialize the stage on slave node. The stage must not use injected traits in this method.
    * @param slaveState
    */
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
   StageResult processAckOnMaster(List<DistStageAck> acks);

   //TODO: remove the call from stages

   /**
    * Should this stage be executed, based on its properties?
    * @return
    */
   boolean shouldExecute();
}
