package org.radargun.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.TopologyHistory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class AbstractTopologyHistory implements TopologyHistory {
   protected final Log log = LogFactory.getLog(getClass());
   protected final List<Event> topologyChanges = new ArrayList<Event>();
   protected final List<Event> hashChanges = new ArrayList<Event>();

   @Override
   public List<TopologyHistory.Event> getTopologyChangeHistory() {
      return deepCopy(topologyChanges);
   }

   @Override
   public List<TopologyHistory.Event> getRehashHistory() {
      return deepCopy(hashChanges);
   }

   private synchronized List<TopologyHistory.Event> deepCopy(List<Event> events) {
      ArrayList<TopologyHistory.Event> newList = new ArrayList<TopologyHistory.Event>(events.size());
      for (Event e : events) newList.add(e.copy());
      return newList;
   }

   protected synchronized void addEvent(List<Event> list, boolean isPre, int atStart, int atEnd) {
      if (isPre) {
         list.add(new Event(false, atStart, atEnd));
      } else {
         int size = list.size();
         if (size == 0 || list.get(size - 1).getEnded() != null) {
            Event ev = new Event(true, atStart, atEnd);
            list.add(ev);
         } else {
            list.get(size - 1).setEnded();
         }
      }
   }

   protected static class Event extends TopologyHistory.Event {
      private final Date started;
      private Date ended;
      private final int atStart;
      private final int atEnd;

      private Event(Date started, Date ended, int atStart, int atEnd) {
         this.started = started;
         this.ended = ended;
         this.atStart = atStart;
         this.atEnd = atEnd;
      }

      public Event(boolean finished, int atStart, int atEnd) {
         if (finished) {
            this.started = this.ended = new Date();
         } else {
            this.started = new Date();
         }
         this.atStart = atStart;
         this.atEnd = atEnd;
      }

      @Override
      public Date getStarted() {
         return started;
      }

      public void setEnded() {
         if (ended != null) throw new IllegalStateException();
         ended = new Date();
      }

      @Override
      public Date getEnded() {
         return ended;
      }

      @Override
      public int getMembersAtStart() {
         return atStart;
      }

      @Override
      public int getMembersAtEnd() {
         return atEnd;
      }

      @Override
      public TopologyHistory.Event copy() {
         return new InfinispanTopologyHistory.Event(started, ended, atStart, atEnd);
      }
   }
}
