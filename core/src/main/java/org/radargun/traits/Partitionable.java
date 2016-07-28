package org.radargun.traits;

import java.util.Set;

@Trait(doc = "Allows the wrapper to simulate partition split.")
public interface Partitionable {
   /**
    * Changes with which members this slave can communicate. Should be run only when the wrapper is started.
    *
    * @param slaveIndex Index of this slave
    * @param members Index of slaves with which this slave can communicate
    */
   void setMembersInPartition(int slaveIndex, Set<Integer> members);

   /**
    * Allows to set the partition before the wrapper is started.
    *
    * @param slaveIndex Index of this slave
    * @param members Index of slaves with which this slave can communicate
    */
   void setStartWithReachable(int slaveIndex, Set<Integer> members);
}
