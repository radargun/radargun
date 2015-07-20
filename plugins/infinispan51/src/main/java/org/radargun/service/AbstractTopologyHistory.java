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

   protected synchronized void addEvent(Map<String, List<Event>> map, String cacheName, TopologyHistory.Event.EventType type, int atStart, int atEnd) {
      List<Event> list = map.get(cacheName);
      if (list == null) {
         map.put(cacheName, list = new ArrayList<>());
      }
      list.add(new Event(new Date(), type, atStart, atEnd));
   }

   protected synchronized void reset() {
      topologyChanges.clear();
      hashChanges.clear();
      cacheStatusChanges.clear();
   }

   protected static class Event extends TopologyHistory.Event {
      private final Date time;
      private final EventType type;
      private final int atStart;
      private final int atEnd;

      public Event(Date time, EventType type, int atStart, int atEnd) {
         this.time = time;
         this.type = type;
         this.atStart = atStart;
         this.atEnd = atEnd;
      }

      @Override
      public Date getTime() {
         return time;
      }

      @Override
      public EventType getType() {
         return type;
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
         return new InfinispanTopologyHistory.Event(time, type, atStart, atEnd);
      }
   }
}
