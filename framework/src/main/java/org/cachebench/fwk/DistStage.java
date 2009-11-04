package org.cachebench.fwk;

import org.cachebench.fwk.state.NodeState;
import org.cachebench.fwk.state.ServerState;

import java.io.Serializable;
import java.util.List;

/**
 * Defines an stage that will be run on both server and nodes.
 *
 * @author Mircea.Markus@jboss.com
 */
public interface DistStage extends Stage, Serializable, Cloneable {

   /**
    * Called on server. Server state should not be passed to the slaves.
    */
   public void initOnServer(ServerState serverState);

   /**
    * Before sending the state to the slave, this will be called to set salve's index.
    */
   public void setNodeIndex(int nodeIndex);

   /**
    * Aftert unmarshalling on the slave, this method will be called to init the stage with slave's state.
    */
   public void initOnNode(NodeState nodeState);

   /**
    * Do whatever on the slave. This will only be called after {@link #executeOnNode()} is called.
    * @return an response that will be serialized and send back to the server.
    */
   DistStageAck executeOnNode();

   /**
    * After all slaves replied through {@link #executeOnNode()}, this method will be called on the server.
    * @return returning false will cause the benchmark to stop.
    */
   boolean processAckOnServer(List<DistStageAck> acks);

   public DistStage clone() throws CloneNotSupportedException;
}
