package org.cachebench.fwk;

import org.cachebench.fwk.state.NodeState;
import org.cachebench.fwk.state.ServerState;

import java.io.Serializable;
import java.util.List;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public interface DistStage extends Stage, Serializable, Cloneable {

   public void initOnServer(ServerState serverState);

   public void setNodeIndex(int nodeIndex);

   public void initOnNode(NodeState nodeState);

   DistStageAck executeOnNode();

   boolean processAckOnServer(List<DistStageAck> acks);

   public DistStage clone() throws CloneNotSupportedException;
}
