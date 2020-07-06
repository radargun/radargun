package org.radargun.stages.topology;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.DefaultConverter;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Partitionable;

/**
 * Stage that partitions the cluster into several parts that cannot communicate
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Partitions the cluster into several parts that cannot communicate.")
public class SetPartitionsStage extends AbstractDistStage {

   @Property(optional = false, converter = UniqueCheckerConverter.class, doc = "Set of sets of partitions, " +
      "e.g. [0,1],[2] makes two partitions, one with workers 0 and 1 and second with worker 2 alone.")
   private List<Set<Integer>> partitions;

   @InjectTrait(dependency = InjectTrait.Dependency.SKIP)
   private Partitionable partitionable;

   public DistStageAck executeOnWorker() {
      if (!shouldExecute() || !isServiceRunning()) {
         return successfulResponse();
      }
      if (partitions == null) {
         return errorResponse("No partitions configured!", null);
      }
      int myPartitionIndex = -1;
      for (int i = 0; i < partitions.size(); ++i) {
         if (partitions.get(i).contains(workerState.getWorkerIndex())) {
            myPartitionIndex = i;
            break;
         }
      }
      if (myPartitionIndex < 0) {
         return errorResponse("Worker " + workerState.getWorkerIndex() + " is not contained in any partition!", null);
      }
      try {
         partitionable.setMembersInPartition(workerState.getWorkerIndex(), partitions.get(myPartitionIndex));
      } catch (Exception e) {
         return errorResponse("Error setting members in partition", e);
      }
      return successfulResponse();
   }

   private static class UniqueCheckerConverter extends DefaultConverter {
      @Override
      public Object convert(String string, Type type) {
         Collection setOfSets = (Collection) super.convert(string, type);
         Set<Object> all = new HashSet<Object>();
         for (Object set : setOfSets) {
            for (Object worker : (Collection) set) {
               if (all.contains(worker)) {
                  throw new IllegalArgumentException("Each worker can be only in one part! Error found for worker " + worker);
               }
               all.add(worker);
            }
         }
         return setOfSets;
      }
   }
}
