package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.utils.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

   private CacheWrapper wrapper;
   private static final String BUCKET = "clusterValidation";

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck response = newDefaultStageAck();      
      try {
         wrapper = slaveState.getCacheWrapper();
         if (wrapper == null) {
            log.info("Missing wrapper, not participating on validation");
            return response;
         }
         int replResult = checkReplicationSeveralTimes();
         if (!partialReplication) {
            if (replResult > 0) {//only executes this on the slaves on which replication happened.
               int index = confirmReplication();
               if (index >= 0) {
                  response.setError(true);
                  response.setErrorMessage("Slave with index " + index + " hasn't confirmed the replication");
                  return response;
               }
            }
         } else {
            log.info("Using partial replication, skipping confirm phase");
         }
         response.setPayload(replResult);
      } catch (Exception e) {
         response.setError(true);
         response.setRemoteException(e);
         return response;
      }
      return response;
   }

   private int confirmReplication() throws Exception {
      wrapper.put(nodeBucket(getSlaveIndex()), confirmationKey(getSlaveIndex()), "true");
      for (int i : getSlaves()) {
         for (int j = 0; j < 10 && (wrapper.get(nodeBucket(i), confirmationKey(i)) == null); j++) {
            tryToPut();
            wrapper.put(nodeBucket(getSlaveIndex()), confirmationKey(getSlaveIndex()), "true");
            Thread.sleep(1000);
         }
         if (wrapper.get(nodeBucket(i), confirmationKey(i)) == null) {
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

   private String nodeBucket(int slaveIndex) {
      return BUCKET + slaveIndex;
   }

   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      logDurationInfo(acks);
      boolean success = true;
      for (DistStageAck ack : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) ack;
         if (defaultStageAck.isError()) {
            log.warn("Ack error from remote slave: " + defaultStageAck);
            return false;
         }
         if (defaultStageAck.getPayload() == null) {
            log.info("Slave " + defaultStageAck.getSlaveIndex() + " did not sent any response");
            continue;
         }
         int replCount = (Integer) defaultStageAck.getPayload();
         if (partialReplication) {
            if (!(replCount > 0)) {
               log.warn("Replication hasn't occurred on slave: " + defaultStageAck);
               success = false;
            }
         } else { //total replication expected
            int expectedRepl = getSlaves().size() - 1;
            if (!(replCount == expectedRepl)) {
               log.warn("On slave " + ack + " total replication hasn't occurred. Expected " + expectedRepl + " and received " + replCount);
               success = false;
            }
         }
      }
      if (!success) log.warn("Cluster hasn't formed!");
      return success;
   }


   private void tryToPut() throws Exception {
      int tryCount = 0;
      while (tryCount < 5) {
         try {
            wrapper.put(nodeBucket(getSlaveIndex()), key(getSlaveIndex()), "true");
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
         if ((partialReplication && replCount >= 1) || (!partialReplication && (replCount == getSlaves().size() - 1))) {
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
      int clusterSize = getActiveSlaveCount();
      int replicaCount = 0;
      for (int i = 0; i < clusterSize; i++) {
         int currentSlaveIndex = getSlaveIndex();
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
            return wrapper.getReplicatedData(nodeBucket(i), key(i));
         } catch (Throwable e) {
            tryCont++;
         }
      }
      return null;
   }

   public Collection<Integer> getSlaves() {
      if (slaves == null) {
         Collection<Integer> list = new ArrayList<Integer>();
         for (int i = 0; i < getActiveSlaveCount(); ++i) {
            list.add(i);
         }
         slaves = list;
      }
      return slaves;
   }

   private String key(int slaveIndex) {
      return KEY + slaveIndex;
   }
}
