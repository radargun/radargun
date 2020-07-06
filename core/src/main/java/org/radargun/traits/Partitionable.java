package org.radargun.traits;

import java.util.Set;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Allows the wrapper to simulate partition split.")
public interface Partitionable {
   /**
    * Changes with which members this worker can communicate. Should be run only when the wrapper is started.
    *
    * @param workerIndex Index of this worker
    * @param members Index of workers with which this worker can communicate
    */
   void setMembersInPartition(int workerIndex, Set<Integer> members);

   /**
    * Allows to set the partition before the wrapper is started.
    *
    * @param workerIndex Index of this worker
    * @param members Index of workers with which this worker can communicate
    */
   void setStartWithReachable(int workerIndex, Set<Integer> members);
}
