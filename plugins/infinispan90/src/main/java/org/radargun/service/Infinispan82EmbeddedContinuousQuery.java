package org.radargun.service;

import org.infinispan.query.Search;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.Query;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public class Infinispan82EmbeddedContinuousQuery implements ContinuousQuery {

   protected final Infinispan82EmbeddedService service;

   public Infinispan82EmbeddedContinuousQuery(Infinispan82EmbeddedService service) {
      this.service = service;
   }

   @Override
   public ListenerReference createContinuousQuery(String cacheName, Query query, ContinuousQuery.Listener cqListener) {
      AbstractInfinispanQueryable.QueryImpl ispnQuery = (AbstractInfinispanQueryable.QueryImpl) query;
      org.infinispan.query.api.continuous.ContinuousQuery cq = Search.getContinuousQuery(service.getCache(cacheName));
      Listener ispnCqListener = new Listener(cqListener);
      cq.addContinuousQueryListener(ispnQuery.getDelegatingQuery(), ispnCqListener);
      return new ListenerReference(cq, ispnCqListener);
   }

   @Override
   public void removeContinuousQuery(String cacheName, ContinuousQuery.ListenerReference listenerReference) {
      ListenerReference ref = (ListenerReference) listenerReference;
      ref.cq.removeContinuousQueryListener(ref.listener);
   }

   private static class Listener implements org.infinispan.query.api.continuous.ContinuousQueryListener {

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

   public static class ListenerReference implements ContinuousQuery.ListenerReference {
      private final org.infinispan.query.api.continuous.ContinuousQuery<Object, Object> cq;
      private final Listener listener;

      public ListenerReference(org.infinispan.query.api.continuous.ContinuousQuery<Object, Object> cq, Listener listener) {
         this.cq = cq;
         this.listener = listener;
      }
   }
}
