package org.radargun.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

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
import org.radargun.traits.CacheListeners;

/**
 * Generic listener is registered only once for each cache, then it multiplexes
 * the events to the RadarGun listeners.
 * The listener registration is not expected to survive cache manager restarts.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanCacheListeners implements CacheListeners {
   protected static final Log log = LogFactory.getLog(InfinispanCacheListeners.class);

   protected final Infinispan70EmbeddedService service;
   protected final ConcurrentMap<String, GenericListener> listeners = new ConcurrentHashMap<String, GenericListener>();
   private InfinispanCacheListeners.CacheManagerListener cacheManagerListener = new CacheManagerListener();

   public InfinispanCacheListeners(Infinispan70EmbeddedService service) {
      this.service = service;
   }

   protected GenericListener getOrCreateListener(String cacheName) {
      if (cacheName == null) {
         cacheName = service.getCache(null).getName();
      }
      GenericListener generic = listeners.get(cacheName);
      if (generic == null) {
         // make sure the cacheManagerListener is registered
         log.trace("Adding cache manager listener");
         service.cacheManager.addListener(cacheManagerListener);

         generic = new GenericListener();
         GenericListener old = listeners.putIfAbsent(cacheName, generic);
         if (old != null) {
            return old;
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
      if (generic == null) throw new IllegalArgumentException("No listener was registered on cache " + cacheName);
      return generic;
   }


   @Override
   public Collection<Type> getSupportedListeners() {
      return Arrays.asList(Type.CREATED, Type.UPDATED, Type.REMOVED, Type.EVICTED);
   }

   @Override
   public void addCreatedListener(String cacheName, CreatedListener listener) {
      getOrCreateListener(cacheName).add(listener);
   }

   @Override
   public void addUpdatedListener(String cacheName, UpdatedListener listener) {
      getOrCreateListener(cacheName).add(listener);
   }

   @Override
   public void addRemovedListener(String cacheName, RemovedListener listener) {
      getOrCreateListener(cacheName).add(listener);
   }

   @Override
   public void addEvictedListener(String cacheName, EvictedListener listener) {
      getOrCreateListener(cacheName).add(listener);
   }

   @Override
   public void addExpiredListener(String cacheName, ExpiredListener listener) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void removeCreatedListener(String cacheName, CreatedListener listener) {
      getListenerOrThrow(cacheName).remove(listener);
   }
   @Override
   public void removeCreatedListeners(String cacheName) {
      getListenerOrThrow(cacheName).created.clear();
   }

   @Override
   public void removeUpdatedListener(String cacheName, UpdatedListener listener) {
      getListenerOrThrow(cacheName).remove(listener);
   }

   @Override
   public void removeUpdatedListeners(String cacheName) {
      getListenerOrThrow(cacheName).updated.clear();
   }

   @Override
   public void removeRemovedListener(String cacheName, RemovedListener listener) {
      getListenerOrThrow(cacheName).remove(listener);
   }

   @Override
   public void removeRemovedListeners(String cacheName) {
      getListenerOrThrow(cacheName).removed.clear();
   }

   @Override
   public void removeEvictedListener(String cacheName, EvictedListener listener) {
      getListenerOrThrow(cacheName).remove(listener);
   }

   @Override
   public void removeEvictedListeners(String cacheName) {
      getListenerOrThrow(cacheName).evicted.clear();
   }

   @Override
   public void removeExpiredListener(String cacheName, ExpiredListener listener) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void removeExpiredListeners(String CacheName) {
      throw new UnsupportedOperationException();
   }

   @Listener(clustered = true)
   protected static class GenericListener {
      CopyOnWriteArraySet<CreatedListener> created = new CopyOnWriteArraySet<CreatedListener>();
      CopyOnWriteArraySet<UpdatedListener> updated = new CopyOnWriteArraySet<UpdatedListener>();
      CopyOnWriteArraySet<RemovedListener> removed = new CopyOnWriteArraySet<RemovedListener>();
      CopyOnWriteArraySet<EvictedListener> evicted = new CopyOnWriteArraySet<EvictedListener>();

      public void add(CreatedListener listener) {
         created.add(listener);
      }

      public void add(UpdatedListener listener) {
         updated.add(listener);
      }

      public void add(RemovedListener listener) {
         removed.add(listener);
      }

      public void add(EvictedListener listener) {
         evicted.add(listener);
      }

      public void remove(CreatedListener listener) {
         created.remove(listener);
      }

      public void remove(UpdatedListener listener) {
         updated.remove(listener);
      }

      public void remove(RemovedListener listener) {
         removed.remove(listener);
      }

      public void remove(EvictedListener listener) {
         evicted.remove(listener);
      }

      @CacheEntryCreated
      public void created(CacheEntryCreatedEvent e) {
         if (e.isPre()) return;
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
         if (e.isPre()) return;
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
         if (e.isPre()) return;
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
         if (e.isPre()) return;
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
