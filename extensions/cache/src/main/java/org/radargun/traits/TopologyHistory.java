package org.radargun.traits;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * The wrapper should be aware of the current topology and its history
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Information about recent network topology changes.")
// TODO: topology and hash are not truly generic - make the histories generic
public interface TopologyHistory {
   /**
    * @return Ordered list of events when the topology was changing (nodes were added/removed, coordinator changed)
    * @param containerName
    */
   List<Event> getTopologyChangeHistory(String containerName);

   /**
    * @return Ordered list of events when the data distribution was changing (rebalancing data)
    * @param containerName
    */
   List<Event> getRehashHistory(String containerName);

   /**
    * @return Ordered list of events when the status of cache was changing (e.g. due to network partition)
    * @param containerName
    */
   List<Event> getCacheStatusChangeHistory(String containerName);

   /**
    * Topology event is a period of time
    */
   abstract class Event {

      private DateFormat formatter = new SimpleDateFormat("HH:mm:ss,S");

      /**
       * @return Date when this event occurred
       */
      public abstract Date getTime();

      /**
       * @return Type of this event - START / END / SINGLE
       */
      public abstract EventType getType();

      /**
       * @return How many members were part of this when this event started.
       */
      public abstract int getMembersAtStart();

      /**
       * @return How many members were part of this when this event ended.
       */
      public abstract int getMembersAtEnd();

      /**
       * @return A deep copy of this event.
       */
      public abstract Event copy();

      @Override
      public boolean equals(Object o) {
         if (o == null) return false;
         if (!(o instanceof Event)) return false;
         Event e = (Event) o;
         return ((getTime() == null && e.getTime() == null)
            || (getTime() != null && getTime().equals(e.getTime())))
            && ((getType() == null && e.getType() == null)
            || (getType() != null && getType().equals(e.getType())));
      }

      @Override
      public int hashCode() {
         int result = getTime().hashCode();
         result = 31 * result + getType().hashCode();
         return result;
      }

      @Override
      public String toString() {
         return String.format("[%s, %s]", getType(), formatter.format(getTime()));
      }

      /**
       * START - start of the event
       * END - end of the event
       * SINGLE - event with no start/end - represents a single point of time
       */
      public enum EventType {
         START, END, SINGLE
      }
   }

   /**
    * Type of history events this class provides.
    */
   enum HistoryType {
      TOPOLOGY, REHASH, CACHE_STATUS
   }
}
