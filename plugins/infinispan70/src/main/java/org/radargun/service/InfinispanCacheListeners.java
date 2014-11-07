package org.radargun.service;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntriesEvicted;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntriesEvictedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStarted;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStopped;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStartedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStoppedEvent;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Generic listener is registered only once for each cache, then it multiplexes the events to the
 * RadarGun listeners. The listener registration is not expected to survive cache manager restarts.
 * 
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanCacheListeners extends
      AbstractInfinispanListeners<InfinispanCacheListeners.GenericCacheListener> {

   protected static final Log log = LogFactory.getLog(InfinispanCacheListeners.class);

   protected final Infinispan70EmbeddedService service;
   private InfinispanCacheListeners.CacheManagerListener cacheManagerListener = new CacheManagerListener();

   public InfinispanCacheListeners(Infinispan70EmbeddedService service) {
      this.service = service;
   }

   protected GenericCacheListener getOrCreateListener(String cacheName, boolean sync) {
      if (cacheName == null) {
         cacheName = service.getCache(null).getName();
      }
      GenericCacheListener generic = listeners.get(cacheName);
      if (((!generic.sync() || !sync)) && (generic.sync() || sync)) {
         listeners.remove(generic);
         generic = null;
      }

      if (generic == null) {
         // make sure the cacheManagerListener is registered
         log.trace("Adding cache manager listener");
         service.cacheManager.addListener(cacheManagerListener);

         generic = sync ? new SyncCacheListener() : new AsyncCacheListener();
         GenericCacheListener old = listeners.putIfAbsent(cacheName, generic);
         if (old != null) {
            if ((old.sync() && sync) || (!old.sync() && !sync)) {
               return old;
            }

            listeners.replace(cacheName, old, generic);
         } else {
            service.getCache(cacheName).getAdvancedCache().addListener(generic);
         }
      }
      return generic;
   }

   protected GenericListener getListenerOrThrow(String cacheName) {
      if (cacheName == null) {
         cacheName = service.getCache(null).getName();
      }
      GenericListener generic = listeners.get(cacheName);
      if (generic == null)
         throw new IllegalArgumentException("No listener was registered on cache " + cacheName);
      return generic;
   }

   @Override
   public Collection<Type> getSupportedListeners() {
      return Arrays.asList(Type.CREATED, Type.UPDATED, Type.REMOVED, Type.EVICTED);
   }

   @Override
   public void addExpiredListener(String cacheName, ExpiredListener listener, boolean sync) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void removeExpiredListener(String cacheName, ExpiredListener listener) {
      throw new UnsupportedOperationException();
   }

   protected static abstract class GenericCacheListener extends AbstractInfinispanListeners.GenericListener {

      @CacheEntryCreated
      public void created(CacheEntryCreatedEvent e) {
         if (e.isPre())
            return;
         for (CreatedListener listener : created) {
            try {
               listener.created(e.getKey(), e.getValue());
            } catch (Throwable t) {
               log.error("Listener " + listener + " has thrown an exception", t);
            }
         }
      }

      @CacheEntryModified
      public void updated(CacheEntryModifiedEvent e) {
         if (e.isPre())
            return;
         for (UpdatedListener listener : updated) {
            try {
               listener.updated(e.getKey(), e.getValue());
            } catch (Throwable t) {
               log.error("Listener " + listener + " has thrown an exception", t);
            }
         }
      }

      @CacheEntryRemoved
      public void updated(CacheEntryRemovedEvent e) {
         if (e.isPre())
            return;
         for (RemovedListener listener : removed) {
            try {
               listener.removed(e.getKey(), e.getOldValue());
            } catch (Throwable t) {
               log.error("Listener " + listener + " has thrown an exception", t);
            }
         }
      }

      @CacheEntriesEvicted
      public void evicted(CacheEntriesEvictedEvent e) {
         if (e.isPre())
            return;
         for (EvictedListener listener : evicted) {
            for (Map.Entry entry : ((Map<?, ?>) e.getEntries()).entrySet()) {
               try {
                  listener.evicted(entry.getKey(), entry.getValue());
               } catch (Throwable t) {
                  log.error("Listener " + listener + " has thrown an exception", t);
               }
            }
         }
      }
      public abstract boolean sync();
   }

   @Listener(clustered = true, sync = true)
   protected static class SyncCacheListener extends GenericCacheListener{

      @Override
      public boolean sync() {
         return true;
      }
   }

   @Listener(clustered = true, sync = false)
   protected static class AsyncCacheListener extends GenericCacheListener {

      @Override
      public boolean sync() {
         return false;
      }
   }

   @Listener
   private class CacheManagerListener {
      @CacheStarted
      public void cacheStarted(CacheStartedEvent e) {
         log.trace("Cache " + e.getCacheName() + " has started");
      }

      @CacheStopped
      public void cacheStopped(CacheStoppedEvent e) {
         log.trace("Cache " + e.getCacheName() + " has stopped");
         listeners.remove(e.getCacheName());
      }
   }
}
