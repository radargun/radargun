package org.radargun.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.infinispan.Cache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.DataRehashed;
import org.infinispan.notifications.cachelistener.annotation.TopologyChanged;
import org.infinispan.notifications.cachelistener.event.DataRehashedEvent;
import org.infinispan.notifications.cachelistener.event.TopologyChangedEvent;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.TopologyHistory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanTopologyHistory implements TopologyHistory {

   protected final Log log = LogFactory.getLog(InfinispanBulkOperations.class);

   private List<Event> topologyChanges = new ArrayList<Event>();
   private List<Event> hashChanges = new ArrayList<Event>();

   protected final Infinispan51EmbeddedService service;

   public InfinispanTopologyHistory(Infinispan51EmbeddedService service) {
      this.service = service;
   }

   void registerListener(Cache<?, ?> cache) {
      cache.addListener(new TopologyAwareListener());
   }

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

   @Listener
   public class TopologyAwareListener {
      @TopologyChanged
      public void onTopologyChanged(TopologyChangedEvent<?,?> e) {
         log.debug("Topology change " + (e.isPre() ? "started" : "finished"));
         int atStart = service.membersCount(e.getConsistentHashAtStart());
         int atEnd = service.membersCount(e.getConsistentHashAtEnd());
         addEvent(topologyChanges, e.isPre(), atStart, atEnd);
      }

      @DataRehashed
      public void onDataRehashed(DataRehashedEvent<?,?> e) {
         log.debug("Rehash " + (e.isPre() ? "started" : "finished"));
         int atStart = e.getMembersAtStart().size();
         int atEnd = e.getMembersAtEnd().size();
         addEvent(hashChanges, e.isPre(), atStart, atEnd);
      }
   }

   private synchronized void addEvent(List<Event> list, boolean isPre, int atStart, int atEnd) {
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

   private static class Event extends TopologyHistory.Event {
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
