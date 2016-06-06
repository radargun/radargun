package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.event.ClientEvents;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public class JDGHotrodContinuousQuery implements ContinuousQuery {

   protected final InfinispanHotrodService service;
   private Object clientListener;

   public JDGHotrodContinuousQuery(InfinispanHotrodService service) {
      this.service = service;
   }

   @Override
   public ListenerReference createContinuousQuery(String cacheName, Query query, ContinuousQuery.Listener cqListener) {
      AbstractInfinispanQueryable.QueryImpl ispnQuery = (AbstractInfinispanQueryable.QueryImpl) query;
      Listener ispnCqListener = new Listener(cqListener);
      Object clientListener = ClientEvents.addContinuousQueryListener(getRemoteCache(cacheName), ispnCqListener, ispnQuery.getDelegatingQuery());
      return new ListenerReference(clientListener);
   }

   @Override
   public void removeContinuousQuery(String cacheName, ContinuousQuery.ListenerReference listenerReference) {
      ListenerReference ref = (ListenerReference) listenerReference;
      getRemoteCache(cacheName).removeClientListener(ref.clientListener);
   }

   protected RemoteCache getRemoteCache(String cacheName) {
      return cacheName == null ? service.managerNoReturn.getCache() : service.managerNoReturn.getCache(cacheName);
   }

   public static class Listener
         implements org.infinispan.client.hotrod.event.ContinuousQueryListener {

      private final ContinuousQuery.Listener cqListener;

      public Listener(ContinuousQuery.Listener cqListener) {
         this.cqListener = cqListener;
      }

      @Override
      public void resultJoining(Object key, Object value) {
         cqListener.onEntryJoined(key, value);
      }

      @Override
      public void resultLeaving(Object key) {
         cqListener.onEntryLeft(key);
      }
   }
   
   private static class ListenerReference implements ContinuousQuery.ListenerReference {
      private final Object clientListener;

      private ListenerReference(Object clientListener) {
         this.clientListener = clientListener;
      }
   }
}
