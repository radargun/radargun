package org.radargun.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.TopologyHistory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractTopologyHistory implements TopologyHistory {
   protected final Log log = LogFactory.getLog(getClass());
   protected final Map<String, List<Event>> topologyChanges = new HashMap<>();
   protected final Map<String, List<Event>> hashChanges = new HashMap<>();
   protected final Map<String, List<Event>> cacheStatusChanges = new HashMap<>();

   protected abstract String getDefaultCacheName();

   @Override
   public synchronized List<TopologyHistory.Event> getTopologyChangeHistory(String cacheName) {
      if (cacheName == null) {
         cacheName = getDefaultCacheName();
      }
      return deepCopy(topologyChanges.get(cacheName));
   }

   @Override
   public synchronized List<TopologyHistory.Event> getRehashHistory(String cacheName) {
      if (cacheName == null) {
         cacheName = getDefaultCacheName();
      }
      return deepCopy(hashChanges.get(cacheName));
   }

   @Override
   public List<TopologyHistory.Event> getCacheStatusChangeHistory(String cacheName) {
      if (cacheName == null) {
         cacheName = getDefaultCacheName();
      }
      return deepCopy(cacheStatusChanges.get(cacheName));
   }

   private List<TopologyHistory.Event> deepCopy(List<Event> events) {
      if (events == null || events.isEmpty()) {
         return Collections.EMPTY_LIST;
      }
      ArrayList<TopologyHistory.Event> newList = new ArrayList<TopologyHistory.Event>(events.size());
      for (Event e : events) {
         newList.add(e.copy());
      }
      return newList;
   }

   protected synchronized void addEvent(Map<String, List<Event>> map, String cacheName, boolean isPre, int atStart, int atEnd) {
      List<Event> list = map.get(cacheName);
      if (list == null) {
         map.put(cacheName, list = new ArrayList<>());
      }
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

   protected synchronized void reset() {
      topologyChanges.clear();
      hashChanges.clear();
      cacheStatusChanges.clear();
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
