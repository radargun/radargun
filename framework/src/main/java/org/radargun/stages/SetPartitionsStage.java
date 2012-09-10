package org.radargun.stages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.Partitionable;
import org.radargun.state.MasterState;

/**
 * Stage that partitions the cluster into several parts that cannot communicate
 * 
 * @author rvansa@redhat.com
 */
public class SetPartitionsStage extends AbstractDistStage {

   private List<Set<Integer>> partitions;
   
   public SetPartitionsStage() {
      super.setExitBenchmarkOnSlaveFailure(true);
   }

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (partitions == null) {
         ack.setError(true);
         ack.setErrorMessage("No partitions configured!");
         return ack;
      }
      int myPartitionIndex = -1;
      for (int i = 0; i < partitions.size(); ++i) {
         if (partitions.get(i).contains(getSlaveIndex())) {
            myPartitionIndex = i;
            break;
         }
      }
      if (myPartitionIndex < 0) {
         ack.setError(true);
         ack.setErrorMessage("Slave " + getSlaveIndex() + " is not contained in any partition!");
         return ack;
      }
      try {
         CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
         if (cacheWrapper != null && cacheWrapper instanceof Partitionable) {
            ((Partitionable) cacheWrapper).setMembersInPartition(getSlaveIndex(), partitions.get(myPartitionIndex));
         } else {
            ack.setError(true);
            ack.setErrorMessage("Cache wrapper does not allow to split partitions");
         }
      } catch (Exception e) {
         ack.setError(true);
         ack.setErrorMessage(e.getMessage());
         log.error(e);         
      }
      return ack;
   }

   @Override
   public void initOnMaster(MasterState masterState, int slaveIndex) {
      super.initOnMaster(masterState, slaveIndex);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("SetPartitionsStage {partitions=");
      for (Set<Integer> partition : partitions) {
         sb.append('(');
         for (int slave : partition) {
            sb.append(slave);
            sb.append(", ");
         }
         sb.append("), ");
      }      
      sb.append(super.toString());
      return sb.toString();
   }
   
   public void setPartitions(String partitions) {
      StringTokenizer tokenizer = new StringTokenizer(partitions, "(),", true);
      List<Set<Integer>> clusterParts = new ArrayList<Set<Integer>>();
      Set<Integer> slaves = new HashSet<Integer>();
      boolean closed = true;
      while (tokenizer.hasMoreTokens()) {
         String token = tokenizer.nextToken();
         if (token.trim().length() == 0) {
            continue;
         } else if ("(".equals(token)) {
            clusterParts.add(new HashSet<Integer>());
            closed = false;
         } else if (")".equals(token)) {
            closed = true;
         } else if (",".equals(token)) {
            // ignore
         } else {
            if (closed) throw new IllegalArgumentException("Invalid partitions: " + partitions);
            try {
               int slave = Integer.parseInt(token);
               if (slaves.contains(slave)) {
                  throw new IllegalArgumentException("Each slave can be only in one part! Error found for slave " + slave);
               }
               clusterParts.get(clusterParts.size() - 1).add(slave);
               slaves.add(slave);
            } catch (NumberFormatException e) {
               throw new IllegalArgumentException("Invalid partitions: " + partitions + "\n" + e);
            }
         }
      }
      this.partitions = clusterParts;
   }
}
