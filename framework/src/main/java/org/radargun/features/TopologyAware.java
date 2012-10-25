package org.radargun.features;

import java.util.Date;
import java.util.List;

public interface TopologyAware {
   List<Event> getTopologyChangeHistory();
   List<Event> getRehashHistory();
   boolean isCoordinator();
   
   abstract class Event {
      public abstract Date getStarted();
      public abstract Date getEnded();
      
      @Override
      public String toString() {
         return "[" + getStarted() + " - " + getEnded() + "]";
      }
   }
}
