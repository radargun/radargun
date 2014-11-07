package org.radargun.traits;

import org.radargun.Operation;

import java.util.Collection;

/**
 * The interface is not in-line with JSR-107, as JCache allows the listener to be invoked
 * anywhere in distributed environment and it's up to the application to delegate it
 * to the node where the listener was registered. This trait expects the listener to be called
 * on the node where it was registered.
 *
 * It is not expected to benchmark execution of the add*Listener methods directly,
 * but you can check what's the delay between executing an operation and the moment listener
 * is called.
 *
 * TODO: start/stop behaviour?
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Allows to listen for events on the cache.")
public interface CacheListeners {

   String TRAIT = CacheListeners.class.getSimpleName();
   Operation CREATED = Operation.register(TRAIT + ".Created");
   Operation EVICTED = Operation.register(TRAIT + ".Evicted");
   Operation REMOVED = Operation.register(TRAIT + ".Removed");
   Operation UPDATED = Operation.register(TRAIT + ".Updated");
   Operation EXPIRED = Operation.register(TRAIT + ".Expired");

   enum Type {
      CREATED,
      UPDATED,
      REMOVED,
      EVICTED,
      EXPIRED
   }

   Collection<Type> getSupportedListeners();

   void addCreatedListener(String cacheName, CreatedListener listener, boolean sync);
   void addUpdatedListener(String cacheName, UpdatedListener listener, boolean sync);
   void addRemovedListener(String cacheName, RemovedListener listener, boolean sync);
   void addEvictedListener(String cacheName, EvictedListener listener, boolean sync);
   void addExpiredListener(String cacheName, ExpiredListener listener, boolean sync);

   void removeCreatedListener(String cacheName, CreatedListener listener);
   void removeUpdatedListener(String cacheName, UpdatedListener listener);
   void removeRemovedListener(String cacheName, RemovedListener listener);
   void removeEvictedListener(String cacheName, EvictedListener listener);
   void removeExpiredListener(String cacheName, ExpiredListener listener);

   interface CreatedListener<K, V> {
      void created(K key, V value);
   }

   interface UpdatedListener<K, V> {
      void updated(K key, V value);
   }

   interface RemovedListener<K, V> {
      void removed(K key, V value);
   }

   interface ExpiredListener<K, V> {
      void expired(K key, V value);
   }

   interface EvictedListener<K, V> {
      void evicted(K key, V value);
   }
}
