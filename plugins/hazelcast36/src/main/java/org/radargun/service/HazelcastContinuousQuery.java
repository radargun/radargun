package org.radargun.service;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
 * @author vjuranek
 */
public class HazelcastContinuousQuery implements ContinuousQuery {

   protected final Hazelcast36Service service;
   private String hazelcastCQListenerId;

   public HazelcastContinuousQuery(Hazelcast36Service service) {
      this.service = service;
   }

   @Override
   public ListenerReference createContinuousQuery(String mapName, Query query, Listener cqListener) {
      EntryListener entryListener = new HazelcastContinuousQueryListener(cqListener);
      hazelcastCQListenerId = getMap(mapName).addEntryListener(entryListener, ((HazelcastQuery) query).getPredicate(), true);
      return new ListenerReference(cqListener, entryListener, hazelcastCQListenerId);
   }

   @Override
   public void removeContinuousQuery(String mapName, ContinuousQuery.ListenerReference listenerReference) {
      if (((HazelcastContinuousQuery.ListenerReference)listenerReference).listenerId != null) {
         getMap(mapName).removeEntryListener(hazelcastCQListenerId);
      }
   }

   protected IMap<Object, Object> getMap(String mapName) {
      return service.getMap(mapName);
   }

   public static class HazelcastContinuousQueryListener implements EntryListener {

      private final Listener cqListener;

      public HazelcastContinuousQueryListener(Listener cqListener) {
         this.cqListener = cqListener;
      }

      @Override
      public void entryAdded(EntryEvent entryEvent) {
         cqListener.onEntryJoined(entryEvent.getKey(), entryEvent.getValue());
      }

      @Override
      public void entryRemoved(EntryEvent entryEvent) {
         cqListener.onEntryLeft(entryEvent.getKey());
      }

      @Override
      public void entryUpdated(EntryEvent entryEvent) {
         //TODO check, if this is correct
         cqListener.onEntryJoined(entryEvent.getKey(), entryEvent.getValue());
      }

      @Override
      public void entryEvicted(EntryEvent entryEvent) {
         cqListener.onEntryLeft(entryEvent.getKey());
      }

      @Override
      public void mapCleared(MapEvent mapEvent) {
      }

      @Override
      public void mapEvicted(MapEvent mapEvent) {
      }
   }
   
   private static class ListenerReference implements ContinuousQuery.ListenerReference {
      private final Listener listener;
      private final EntryListener entryListener;
      private final String listenerId;
      

      private ListenerReference(Listener listener, EntryListener entryListener, String listenerId) {
         this.listener = listener;
         this.entryListener = entryListener;
         this.listenerId = listenerId;
      }
   }
}
