package org.radargun.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryModified;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

import static org.radargun.service.AbstractInfinispanListeners.GenericListener;

/**
 * 
 * Generic Infinispan remote listeners, similar to {@link InfinispanCacheListeners}.
 * 
 * @author vjuranek
 * 
 */
public class InfinispanClientListeners extends AbstractInfinispanListeners {

   protected static final Log log = LogFactory.getLog(InfinispanClientListeners.class);
  
   protected final Infinispan70HotrodService service;
   protected final ConcurrentMap<String, GenericClientListener> listeners = new ConcurrentHashMap<String, GenericClientListener>();

   public InfinispanClientListeners(Infinispan70HotrodService service) {
      this.service = service;
   }

   @Override
   protected GenericClientListener getOrCreateListener(String cacheName, boolean sync) {
      final RemoteCacheManager remoteManager = service.getRemoteManager(false);
      if (cacheName == null)
         cacheName = remoteManager.getCache().getName();

      GenericClientListener listenerContainer = listeners.putIfAbsent(cacheName, new GenericClientListener());
      if (listenerContainer == null) {
         listenerContainer = listeners.get(cacheName);
         service.getRemoteManager(false).getCache(cacheName).addClientListener(listenerContainer);
         service.getRemoteManager(true).getCache(cacheName).addClientListener(listenerContainer);
      }
      return listenerContainer;
   }

   @Override
   protected GenericClientListener getListenerOrThrow(String cacheName, boolean sync) {
      if (cacheName == null) {
         final RemoteCacheManager remoteManager = service.getRemoteManager(false);
         cacheName = remoteManager.getCache().getName();
      }
      GenericClientListener listenerContainer = listeners.get(cacheName);
      if (listenerContainer == null)
         throw new IllegalArgumentException("No listener was registered on cache " + cacheName);
      return listenerContainer;
   }

   @Override
   public Collection<Type> getSupportedListeners() {
      return Arrays.asList(Type.CREATED, Type.UPDATED, Type.REMOVED);
   }

   @Override
   public void addEvictedListener(String cacheName, EvictedListener listener, boolean sync) {
      throw new UnsupportedOperationException("HotRod doesn't support client listeners for eviction");
   }

   @Override
   public void addExpiredListener(String cacheName, ExpiredListener listener, boolean sync) {
      throw new UnsupportedOperationException("HotRod doesn't support client listeners for expiration");
   }

   @Override
   public void removeEvictedListener(String cacheName, EvictedListener listener, boolean sync) {
      throw new UnsupportedOperationException("HotRod doesn't support client listeners for eviction");
   }

   @Override
   public void removeExpiredListener(String cacheName, ExpiredListener listener, boolean sync) {
      throw new UnsupportedOperationException("HotRod doesn't support client listeners for expiration");
   }

   @Override
   public void removeListener(String cacheName, boolean sync) {
      GenericClientListener listener = getListenerOrThrow(cacheName, sync);
      if (listener.isEmpty()) {
         service.getRemoteManager(false).getCache(cacheName).removeClientListener(listener);
         service.getRemoteManager(true).getCache(cacheName).removeClientListener(listener);
      }
   }

   @ClientListener
   public static class GenericClientListener extends AbstractInfinispanListeners.GenericListener {

      @ClientCacheEntryCreated
      public void created(ClientCacheEntryCreatedEvent e) {
         for (CreatedListener listener : created) {
            try {
               listener.created(e.getKey(), e.getVersion());
            } catch (Exception ex) {
               log.error("Listener " + listener + " has thrown an exception", ex);
            }
         }
      }

      @ClientCacheEntryModified
      public void updated(ClientCacheEntryModifiedEvent e) {
         for (UpdatedListener listener : updated) {
            try {
               listener.updated(e.getKey(), e.getVersion());
            } catch (Exception ex) {
               log.error("Listener " + listener + " has thrown an exception", ex);
            }
         }
      }

      @ClientCacheEntryRemoved
      public void updated(ClientCacheEntryRemovedEvent e) {
         for (RemovedListener listener : removed) {
            try {
               listener.removed(e.getKey(), null);
            } catch (Exception ex) {
               log.error("Listener " + listener + " has thrown an exception", ex);
            }
         }
      }
   }

}
