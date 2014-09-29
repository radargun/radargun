package org.radargun.stages.cache;

import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.Utils;

/**
 * Distributed stage that would validate that cluster is correctly formed.
 * <pre>
 * Algorithm:
 * - each slave does a put(slaveIndex);
 * - each slave checks whether all (or part) of the remaining slaves replicated here.
 *
 * Config:
 *   - 'partialReplication' : is set to true, then the slave will consider that the cluster is formed when one slave
 *      replicated here. If false (default value) then replication will only be considered successful if all
 * (clusterSize)
 *      slaves replicated here.
 * </pre>
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Verifies that the cluster is formed by injecting an entry into the cache and then reading it from other nodes.")
public class ClusterValidationStage extends AbstractDistStage {

   private static final String KEY = "_InstallBenchmarkStage_";
   private static final String CONFIRMATION_KEY = "_confirmation_";

   @Property(doc = "If set to true, then the slave will consider that the cluster is formed when one slave " +
         "replicated the control entry. Otherwise the replication will only be considered successful if all " +
         "slaves replicated the control value. Default is false.")
   private boolean partialReplication = false;

   @Property(doc = "How many times we should try to retrieve the control entry.")
   private int replicationTryCount = 60;

   @Property(doc = "Delay between attempts to retrieve the control entry.")
   private int replicationTimeSleep = 2000;

   @InjectTrait
   private BasicOperations basicOperations;

   private BasicOperations.Cache cache;

   public DistStageAck executeOnSlave() {
      try {
         if (!isServiceRunning()) {
            log.info("Missing wrapper, not participating on validation");
            return successfulResponse();
         }
         cache = basicOperations.getCache(null);
         int replResult = checkReplicationSeveralTimes();
         if (!partialReplication) {
            if (replResult > 0) {//only executes this on the slaves on which replication happened.
               int index = confirmReplication();
               if (index >= 0) {
                  return errorResponse("Slave with index " + index + " hasn't confirmed the replication");
               }
            }
         } else {
            log.info("Using partial replication, skipping confirm phase");
         }
         return new ReplicationAck(slaveState, replResult);
      } catch (Exception e) {
         return errorResponse("Exception thrown", e);
      }
   }

   private int confirmReplication() throws Exception {
      cache.put(confirmationKey(slaveState.getSlaveIndex()), "true");
      for (int i : getExecutingSlaves()) {
         for (int j = 0; j < 10 && (cache.get(confirmationKey(i)) == null); j++) {
            tryToPut();
            cache.put(confirmationKey(slaveState.getSlaveIndex()), "true");
            Thread.sleep(1000);
         }
         if (cache.get(confirmationKey(i)) == null) {
            log.warn("Confirm phase unsuccessful. Slave " + i + " hasn't acknowledged the test");
            return i;
         }
      }
      log.info("Confirm phase successful.");
      return -1;
   }

   private String confirmationKey(int slaveIndex) {
      return CONFIRMATION_KEY + slaveIndex;
   }

   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      logDurationInfo(acks);
      StageResult result = StageResult.SUCCESS;
      for (DistStageAck ack : acks) {
         if (ack.isError()) {
            log.warn("Ack error from remote slave: " + ack);
            result = errorResult();
         }
         if (!(ack instanceof ReplicationAck)) {
            log.info("Slave " + ack.getSlaveIndex() + " did not sent any response");
            continue;
         }
         ReplicationAck replicationAck = (ClusterValidationStage.ReplicationAck) ack;
         if (partialReplication) {
            if (replicationAck.result <= 0) {
               log.warn("Replication hasn't occurred on slave: " + ack);
               result = errorResult();
            }
         } else { //total replication expected
            int expectedRepl = getExecutingSlaves().size() - 1;
            if (!(replicationAck.result == expectedRepl)) {
               log.warn("On slave " + ack + " total replication hasn't occurred. Expected " + expectedRepl + " and received " + replicationAck.result);
               result = errorResult();
            }
         }
      }
      if (result.isError()) log.warn("Cluster hasn't formed!");
      return result;
   }


   private void tryToPut() throws Exception {
      int tryCount = 0;
      while (tryCount < 5) {
         try {
            cache.put(key(slaveState.getSlaveIndex()), "true");
            return;
         } catch (Throwable e) {
            log.warn("Error while trying to put data: ", e);
            tryCount++;
         }
      }
      throw new Exception("Couldn't accomplish addition before replication!");
   }


   private int checkReplicationSeveralTimes() throws Exception {
      tryToPut();
      int replCount = 0;      
      for (int i = 0; i < replicationTryCount; i++) {
         replCount = replicationCount();
         if ((partialReplication && replCount >= 1) || (!partialReplication && (replCount == getExecutingSlaves().size() - 1))) {
            log.info("Replication test successfully passed. partialReplication? " + partialReplication + ", replicationCount = " + replCount);
            return replCount;
         }
         //adding our stuff one more time
         tryToPut();
         log.info("Replication test failed, " + (i + 1) + " tries so far. Sleeping for " + Utils.prettyPrintMillis(replicationTimeSleep)
               + " and trying again.");
         Thread.sleep(replicationTimeSleep);
      }
      log.info("Replication test failed. Last replication count is " + replCount);
      return -1;
   }

   private int replicationCount() throws Exception {
      int clusterSize = slaveState.getClusterSize();
      int replicaCount = 0;
      for (int i = 0; i < clusterSize; i++) {
         int currentSlaveIndex = slaveState.getSlaveIndex();
         if (i == currentSlaveIndex) {
            continue;
         }
         Object data = tryGet(i);
         if (data == null || !"true".equals(data.toString())) {
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
            return cache.get(key(i));
         } catch (Throwable e) {
            tryCont++;
         }
      }
      return null;
   }

   private String key(int slaveIndex) {
      return KEY + slaveIndex;
   }

   private static class ReplicationAck extends DistStageAck {
      final int result;

      private ReplicationAck(SlaveState slaveState, int result) {
         super(slaveState);
         this.result = result;
      }
   }
}
