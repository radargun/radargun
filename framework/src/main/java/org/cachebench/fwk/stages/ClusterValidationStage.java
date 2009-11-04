package org.cachebench.fwk.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.fwk.DistStageAck;

import java.util.Arrays;
import java.util.List;

/**
 * // TODO: Mircea - Document this! //todo - move CacheWrapper start code one stage behind
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClusterValidationStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(ClusterValidationStage.class);


   //todo make this params configurable
   private static final int REPLICATION_TRY_COUNT = 17;
   private static final int REPLICATION_TRY_SLEEP = 2000;

   private static final String PREFIX = "_InstallBenchmarkStage_";


   private boolean isPartialReplication = false;
   private CacheWrapper wrapper;

   public DistStageAck executeOnNode() {
      DefaultDistStageAck response = newDefaultStageAck();
      try {
         wrapper = nodeState.getCacheWrapper();
         tryToPut();
         int replResult = checkReplicationSeveralTimes();
         response.setPayload(replResult);
      } catch (Exception e) {
         response.setError(true);
         response.setRemoteException(e);
         return response;
      }
      return response;
   }


   private void tryToPut() throws Exception {
      int tryCount = 0;
      while (tryCount < 5) {
         try {
            wrapper.put(Arrays.asList(PREFIX, "" + nodeState.getNodeIndex()), PREFIX + nodeState.getNodeIndex(), "true");
            return;
         }
         catch (Throwable e) {
            log.warn("Error while trying to put data: ", e);
            tryCount++;
         }
      }
      throw new Exception("Couldn't accomplish additiona before replication!");
   }


   private int checkReplicationSeveralTimes() throws Exception {
      int replCount = 0;
      for (int i = 0; i < REPLICATION_TRY_COUNT; i++) {
         replCount = replicationCount();
         if ((isPartialReplication && replCount >= 1) || (!isPartialReplication && (replCount == nodeState.getClusterSize() - 1))) {
            log.info("Replication test successfully passed. isPartialReplication? " + isPartialReplication + ", replicationCount = " + replCount);
            return replCount;
         }
         log.info("Replication test failed, " + (i + 1) + " tries so far. Sleeping for  " + REPLICATION_TRY_SLEEP
               + " millis then try again");
         Thread.sleep(REPLICATION_TRY_SLEEP);
      }
      log.info("Replication test failed. Last replication count is " + replCount);
      return -1;
   }

   private int replicationCount() throws Exception {
      int clusterSize = nodeState.getClusterSize();
      int replicaCount = 0;
      for (int i = 0; i < clusterSize; i++) {
         int currentNodeIndex = nodeState.getNodeIndex();
         if (i == currentNodeIndex) {
            continue;
         }
         Object data = tryGet(i);
         if (data == null || !"true".equals(data)) {
            log.trace("Cache with index " + i + " did *NOT* replicate");
         } else {
            log.trace("Cache with index " + i + " replicated here ");
            replicaCount++;
         }
      }
      log.info("Number of caches that replicated here is " + replicaCount);
      return replicaCount;
   }


   private Object tryGet(int i) throws Exception {
      int tryCont = 0;
      while (tryCont < 5) {
         try {
            return wrapper.getReplicatedData(Arrays.asList(PREFIX, "" + i), PREFIX + i);
         }
         catch (Throwable e) {
            tryCont++;
         }
      }
      return null;
   }

   public boolean processAckOnServer(List<DistStageAck> acks) {
      boolean success = true;
      for (DistStageAck ack : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) ack;
         if (defaultStageAck.isError()) {
            log.warn("Ack error from remote node: " + defaultStageAck, defaultStageAck.getRemoteException());
            return false;
         }
         int replCount = (Integer) defaultStageAck.getPayload();
         if (isPartialReplication) {
            if (!(replCount > 0)) {
               log.warn("Replication hasn't occured on node: " + defaultStageAck);
               success = false;
            }
         } else { //total replication expected
            int expectedRepl = serverConfig.getNodeCount() - 1;
            if (! (replCount == expectedRepl)) {
               log.warn("On node " + ack + " total repl hasn't occured. Expected " + expectedRepl + " and received " + replCount);
               success = false;
            }
         }
      }
      if (success) {
         log.info("Cluster successfully formed!");
      }
      return success;
   }


   public void setPartialReplication(boolean partialReplication) {
      isPartialReplication = partialReplication;
   }
}
