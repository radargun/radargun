package org.cachebench.fwk.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.fwk.DistStageAck;
import org.cachebench.fwk.state.NodeState;
import org.cachebench.fwk.state.ServerState;
import org.cachebench.utils.Instantiator;

import java.util.List;
import java.util.Map;

/**
 * // TODO: Mircea - Document this!
 *
 * @author
 */
public class StartClusterStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(StartClusterStage.class);

   private int clusterSize;
   private String chacheWrapperClass;
   private Map<String, String> wrapperStartupParams;

   public void initOnServer(ServerState serverState) {
      super.initOnServer(serverState);
      this.clusterSize = serverState.getConfig().getNodeCount();
   }

   @Override
   public void initOnNode(NodeState nodeState) {
      super.initOnNode(nodeState);
      nodeState.setNodeIndex(nodeIndex);
      nodeState.setClusterSize(clusterSize);
   }

   public DistStageAck executeOnNode() {
      log.info("Ack server's StartCluster stage. Local address is: " + nodeState.getLocalAddress() + ". This node's index is: " + nodeState.getNodeIndex());
      CacheWrapper wrapper;
      DefaultDistStageAck ack = newDefaultStageAck();
      try {
         wrapper = (CacheWrapper) Instantiator.getInstance().createClass(chacheWrapperClass);
         wrapper.init(wrapperStartupParams);
         wrapper.setUp();
         nodeState.setCacheWrapper(wrapper);
      } catch (Exception e) {
         log.error("Issues while instantiating/starting cache wrapper",e);
         ack.setError(true);
         ack.setRemoteException(e);
         return ack;
      }
      log.info("Successfully started cache wrapper on node " + nodeState.getNodeIndex() + ": " + wrapper);
      return ack;
   }

   public boolean processAckOnServer(List<DistStageAck> acks) {
      log.info("Received ack from all (" + acks.size() + ") nodes.");
      for (DistStageAck stageAck : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) stageAck;
         if (defaultStageAck.isError()) {
            log.warn("Received error ack " + defaultStageAck, defaultStageAck.getRemoteException());
            return false;
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack meessagess were successful");
      return true;
   }

   public StartClusterStage clone() throws CloneNotSupportedException {
      return (StartClusterStage) super.clone();
   }


   public void setChacheWrapperClass(String chacheWrapperClass) {
      this.chacheWrapperClass = chacheWrapperClass;
   }

   public void setWrapperStartupParams(Map<String, String> wrapperStartupParams) {
      this.wrapperStartupParams = wrapperStartupParams;
   }
}
