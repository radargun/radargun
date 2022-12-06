package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCache;
import org.radargun.traits.ContinuousQuery;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
public abstract class InfinispanHotrodContinuousQuery implements ContinuousQuery {

   protected final InfinispanHotrodService service;

   public InfinispanHotrodContinuousQuery(InfinispanHotrodService service) {
      this.service = service;
   }

   @Override
   public void removeContinuousQuery(String cacheName, ContinuousQuery.ListenerReference listenerReference) {
      ListenerReference ref = (ListenerReference) listenerReference;
      getRemoteCache(cacheName).removeClientListener(ref.clientListener);
   }

   protected RemoteCache getRemoteCache(String cacheName) {
      return cacheName == null ? service.managerNoReturn.getCache() : service.managerNoReturn.getCache(cacheName);
   }

   private static class ListenerReference implements ContinuousQuery.ListenerReference {
      private final Object clientListener;

      private ListenerReference(Object clientListener) {
         this.clientListener = clientListener;
      }
   }
}
