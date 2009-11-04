package org.cachebench.fwk.stages;

import org.cachebench.fwk.ServerConfig;
import org.cachebench.fwk.DistStage;
import org.cachebench.fwk.state.NodeState;
import org.cachebench.fwk.state.ServerState;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public abstract class AbstractDistStage implements DistStage {

   protected transient NodeState nodeState;

   protected transient ServerConfig serverConfig;

   protected transient ServerState serverState;

   protected int nodeIndex;

   public void initOnNode(NodeState nodeState) {
      this.nodeState = nodeState;
   }

   public void initOnServer(ServerState serverState) {
      this.serverState = serverState;
      this.serverConfig = serverState.getConfig();
      assert serverConfig != null;
   }

   public void setNodeIndex(int nodeIndex) {
      this.nodeIndex = nodeIndex;
   }

   protected DefaultDistStageAck newDefaultStageAck() {
      return new DefaultDistStageAck(nodeState.getNodeIndex(), nodeState.getLocalAddress());
   }

   public DistStage clone() throws CloneNotSupportedException {
      return (DistStage) super.clone();
   }
}
