package org.radargun.features;

import java.util.Date;
import java.util.List;

/**
 * The wrapper should be aware of the current topology and its history
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface TopologyAware {
   /**
    * @return Ordered list of events when the topology was changing (nodes were added/removed, coordinator changed)
    */
   List<Event> getTopologyChangeHistory();

   /**
    * @return Ordered list of events when the data distribution was changing (rebalancing data)
    */
   List<Event> getRehashHistory();

   /**
    * @return True if this slave has unique role in the cluster
    */
   boolean isCoordinator();

   /**
    * Topology event is a period of time
    */
   abstract class Event {
      /**
       * @return Date when this event started
       */
      public abstract Date getStarted();

      /**
       * @return Date when this event finished or null if it has not finished yet.
       */
      public abstract Date getEnded();
      
      @Override
      public String toString() {
         return "[" + getStarted() + " - " + getEnded() + "]";
      }
   }
}
