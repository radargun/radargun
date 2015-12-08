package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.VersionedValue;
import org.infinispan.util.concurrent.NotifyingFuture;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.TemporalOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the {@link BasicOperations} and {@link ConditionalOperations}
 * through the HotRod protocol.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HotRodOperations implements BasicOperations, BulkOperations, ConditionalOperations, TemporalOperations {
   protected final static Log log = LogFactory.getLog(HotRodOperations.class);
   protected final static boolean trace = log.isTraceEnabled();
   protected final InfinispanHotrodService service;

   public HotRodOperations(InfinispanHotrodService service) {
      this.service = service;
   }

   @Override
   public <K, V> HotRodCache<K, V> getCache(String cacheName) {
      if (cacheName == null) {
         cacheName = service.cacheName;
      }
      if (cacheName == null) {
         return new HotRodCache<>((RemoteCache<K,V>) service.managerNoReturn.getCache(false), (RemoteCache<K,V>) service.managerForceReturn.getCache(true));
      } else {
         return new HotRodCache<>((RemoteCache<K,V>) service.managerNoReturn.getCache(cacheName, false), (RemoteCache<K,V>) service.managerForceReturn.getCache(cacheName, true));
      }
   }

   @Override
   public <K, V> BulkOperations.Cache<K, V> getCache(String cacheName, boolean preferAsync) {
      if (cacheName == null) {
         cacheName = service.cacheName;
      }
      if (cacheName == null) {
         return new HotRodBulkOperationsCache<>((RemoteCache<K,V>) service.managerNoReturn.getCache(false), (RemoteCache<K,V>) service.managerForceReturn.getCache(true), preferAsync);
      } else {
         return new HotRodBulkOperationsCache<>((RemoteCache<K,V>) service.managerNoReturn.getCache(cacheName, false), (RemoteCache<K,V>) service.managerForceReturn.getCache(cacheName, true), preferAsync);
      }
   }

   protected class HotRodCache<K, V> implements BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V>, TemporalOperations.Cache<K, V> {

      protected final RemoteCache<K, V> noReturn;
      protected final RemoteCache<K, V> forceReturn;

      public HotRodCache(RemoteCache<K, V> noReturn, RemoteCache<K, V> forceReturn) {
         this.noReturn = noReturn;
         this.forceReturn = forceReturn;
      }

      @Override
      public V get(K key) {
         if (trace) log.tracef("GET cache=%s key=%s", noReturn.getName(), key);
         // causes a warning on server, and Get always returns the value
         return noReturn.get(key);
      }

      @Override
      public boolean containsKey(K key) {
         if (trace) log.tracef("CONTAINS cache=%s key=%s", forceReturn.getName(), key);
         return forceReturn.containsKey(key);
      }

      @Override
      public void put(K key, V value) {
         if (trace) log.tracef("PUT cache=%s key=%s value=%s", noReturn.getName(), key, value);
         noReturn.put(key, value);
      }

      @Override
      public V getAndPut(K key, V value) {
         if (trace) log.tracef("GET_AND_PUT cache=%s key=%s value=%s", forceReturn.getName(), key, value);
         return forceReturn.put(key, value);
      }

      @Override
      public boolean remove(K key) {
         if (trace) log.tracef("REMOVE cache=%s key=%s", forceReturn.getName(), key);
         return forceReturn.remove(key) != null;
      }

      @Override
      public V getAndRemove(K key) {
         if (trace) log.tracef("GET_AND_REMOVE cache=%s key=%s", forceReturn.getName(), key);
         return forceReturn.remove(key);
      }

      @Override
      public void clear() {
         if (trace) log.trace("CLEAR " + noReturn.getName());
         noReturn.clear();
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         if (trace) log.tracef("PUT_IF_ABSENT cache=%s key=%s value=%s", forceReturn.getName(), key, value);
         return forceReturn.putIfAbsent(key, value) == null;
      }

      @Override
      public boolean remove(K key, V oldValue) {
         if (trace) log.tracef("REMOVE cache=%s key=%s value=%s", forceReturn.getName(), key, oldValue);
         for (;;) {
            VersionedValue<V> versioned = forceReturn.getVersioned(key);
            if (oldValue == null || versioned == null) {
               return false;
            }
            if (oldValue.equals(versioned.getValue())) {
               if (forceReturn.removeWithVersion(key, versioned.getVersion())) {
                  return true;
               }
               if (trace) log.tracef("Remove with version %d failed", versioned.getVersion());
            } else {
               return false;
            }
         }
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         if (trace) log.tracef("REPLACE cache=%s key=%s old=%s, new=%s", forceReturn.getName(), key, oldValue, newValue);
         for (;;) {
            VersionedValue<V> versioned = forceReturn.getVersioned(key);
            if (oldValue == null || versioned == null) {
               return false;
            }
            if (oldValue.equals(versioned.getValue())) {
               if (forceReturn.replaceWithVersion(key, newValue, versioned.getVersion())) {
                  return true;
               }
               if (trace) log.tracef("Replace with version %d failed", versioned.getVersion());
            } else {
               return false;
            }
         }
      }

      @Override
      public boolean replace(K key, V value) {
         if (trace) log.tracef("REPLACE cache=%s key=%s value=%s", forceReturn.getName(), key, value);
         return forceReturn.replace(key, value) != null;
      }

      @Override
      public V getAndReplace(K key, V value) {
         if (trace) log.tracef("GET_AND_REPLACE cache=%s key=%s value=%s", forceReturn.getName(), key, value);
         return forceReturn.replace(key, value);
      }

      @Override
      public void put(K key, V value, long lifespan) {
         if (trace) log.tracef("PUT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s", noReturn.getName(), key, value, lifespan);
         noReturn.put(key, value, lifespan, TimeUnit.MILLISECONDS);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan) {
         if (trace) log.tracef("GET_AND_PUT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s", forceReturn.getName(), key, value, lifespan);
         return forceReturn.put(key, value, lifespan, TimeUnit.MILLISECONDS);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan) {
         if (trace) log.tracef("PUT_IF_ABSENT_WITH_LIFESPAN cache=%s key=%s value=%s lifespan=%s", forceReturn.getName(), key, value, lifespan);
         return forceReturn.putIfAbsent(key, value, lifespan, TimeUnit.MILLISECONDS) == null;
      }

      @Override
      public void put(K key, V value, long lifespan, long maxIdleTime) {
         if (trace) log.tracef("PUT_WITH_LIFESPAN_AND_MAXIDLE cache=%s key=%s value=%s lifespan=%s maxIdle=%s", noReturn.getName(), key, value, lifespan, maxIdleTime);
         noReturn.put(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
      }

      @Override
      public V getAndPut(K key, V value, long lifespan, long maxIdleTime) {
         if (trace) log.tracef("GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE cache=%s key=%s value=%s lifespan=%s maxIdle=%s", forceReturn.getName(), key, value, lifespan, maxIdleTime);
         return forceReturn.put(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
      }

      @Override
      public boolean putIfAbsent(K key, V value, long lifespan, long maxIdleTime) {
         if (trace) log.tracef("PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE cache=%s key=%s value=%s lifespan=%s maxIdle=%s", forceReturn.getName(), key, value, lifespan, maxIdleTime);
         return forceReturn.putIfAbsent(key, value, lifespan, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS) == null;
      }

   }

   protected class HotRodBulkOperationsCache<K, V> extends HotRodCache<K, V> implements BulkOperations.Cache<K, V> {

      protected final boolean preferAsync;

      public HotRodBulkOperationsCache(RemoteCache<K, V> noReturn, RemoteCache<K, V> forceReturn, boolean preferAsync) {
         super(noReturn, forceReturn);
         this.preferAsync = preferAsync;
      }

      @Override
      public Map<K, V> getAll(Set<K> keys) {
         if (trace) log.tracef("GET_ALL cache=%s keys=%s", forceReturn.getName(), keys);
         Map<K, V> result = new HashMap<>(keys.size());
         if (preferAsync) {
            Map<K, Future<V>> futureMap = new HashMap<>(keys.size());
            for (K key : keys) {
               futureMap.put(key, forceReturn.getAsync(key));
            }
            for (Map.Entry<K, Future<V>> entry : futureMap.entrySet()) {
               try {
                  V value = entry.getValue().get();
                  if (value != null) {
                     result.put(entry.getKey(), value);
                  }
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            }
         } else {
            for (K key : keys) {
               result.put(key, forceReturn.get(key));
            }
         }
         return result;
      }

      @Override
      public void putAll(Map<K, V> entries) {
         if (trace) log.tracef("PUT_ALL cache=%s keys=%s", noReturn.getName(), entries);
         if (preferAsync) {
            noReturn.putAllAsync(entries);
         } else {
            noReturn.putAll(entries);
         }
      }

      @Override
      public void removeAll(Set<K> keys) {
         if (trace) log.tracef("REMOVE_ALL cache=%s keys=%s", forceReturn.getName(), keys);
         if (preferAsync) {
            Map<K, Future<V>> futureMap = new HashMap<>(keys.size());
            for (K key : keys) {
               futureMap.put(key, forceReturn.removeAsync(key));
            }
            for (Map.Entry<K, Future<V>> entry : futureMap.entrySet()) {
               try {
                  entry.getValue().get();
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            }
         } else {
            for (K key : keys) {
               noReturn.remove(key);
            }
         }
      }
   }
}
