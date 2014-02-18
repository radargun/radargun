package org.radargun.stages;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.config.DefaultConverter;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.features.Partitionable;

/**
 * Stage that partitions the cluster into several parts that cannot communicate
 * 
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Partitions the cluster into several parts that cannot communicate.")
public class SetPartitionsStage extends AbstractDistStage {

   @Property(optional = false, converter = UniqueCheckerConverter.class, doc = "Set of sets of partitions," +
         "e.g. [0,1],[2] makes two partitions, one with slaves 0 and 1 and second with slave 2 alone.")
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
         if (partitions.get(i).contains(slaveState.getSlaveIndex())) {
            myPartitionIndex = i;
            break;
         }
      }
      if (myPartitionIndex < 0) {
         ack.setError(true);
         ack.setErrorMessage("Slave " + slaveState.getSlaveIndex() + " is not contained in any partition!");
         return ack;
      }
      try {
         CacheWrapper cacheWrapper = slaveState.getCacheWrapper();
         if (cacheWrapper == null) {
            log.info("Cache wrapper not running, ignoring request to partition");
            return ack;
         }
         if (cacheWrapper instanceof Partitionable) {
            ((Partitionable) cacheWrapper).setMembersInPartition(slaveState.getSlaveIndex(), partitions.get(myPartitionIndex));
         } else {
            String message = "Cache wrapper " + cacheWrapper.getClass() + "does not allow to split partitions";
            ack.setError(true);
            ack.setErrorMessage(message);
            log.error(message);
         }
      } catch (Exception e) {
         String message = "Error setting members in partition";
         log.error(message, e);
         ack.setError(true);
         ack.setErrorMessage(message);
         ack.setRemoteException(e);
      }
      return ack;
   }

   public static class UniqueCheckerConverter extends DefaultConverter {
      @Override
      public Object convert(String string, Type type) {
         Collection setOfSets = (Collection) super.convert(string, type);
         Set<Object> all = new HashSet<Object>();
         for (Object set : setOfSets) {
            for (Object slave : (Collection) set) {
               if (all.contains(slave)) {
                  throw new IllegalArgumentException("Each slave can be only in one part! Error found for slave " + slave);
               }
               all.add(slave);
            }
         }
         return setOfSets;
      }
   }
}
