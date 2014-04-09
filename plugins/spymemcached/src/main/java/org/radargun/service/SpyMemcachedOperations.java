package org.radargun.service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;

/**
 * Implementation of {@link BasicOperations} and {@link ConditionalOperations}
 * through the Memcached protocol, using SpyMemcached implementation.
 * The memcached CAS operation is used to implement some of the operations,
 * therefore, some operations may require multiple actual calls.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class SpyMemcachedOperations implements BasicOperations, ConditionalOperations {
   private final SpyMemcachedService service;

   public SpyMemcachedOperations(SpyMemcachedService service) {
      this.service = service;
   }

   @Override
   public <K, V> SpyMemcachedCache<K, V> getCache(String cacheName) {
      if (cacheName != null && (service.cacheName == null || !service.cacheName.equals(cacheName))) {
         throw new UnsupportedOperationException();
      }
      try {
         return new SpyMemcachedCache();
      } catch (IOException e) {
         throw new RuntimeException("Cannot connect client", e);
      }
   }

   protected class SpyMemcachedCache<K, V> implements BasicOperations.Cache<K, V>, ConditionalOperations.Cache<K, V> {

      private final MemcachedClient client;

      public SpyMemcachedCache() throws IOException {
         client = new MemcachedClient(service.servers);
      }

      @Override
      public V get(K key) {
         return (V) client.get(key.toString());
      }

      @Override
      public boolean containsKey(K key) {
         return client.get(key.toString()) != null;
      }

      @Override
      public void put(K key, V value) {
         try {
            if (!client.set(key.toString(), 0, value).get()) {
               throw new IllegalStateException("PUT failed");
            }
         } catch (InterruptedException e) {
            throw new IllegalStateException(e);
         } catch (ExecutionException e) {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public V getAndPut(K key, V value) {
         String stringKey = key.toString();
         CASResponse response;
         for (;;) {
            CASValue<Object> prev = client.gets(stringKey);
            if (prev.getValue() == null) {
               try {
                  response = client.add(stringKey, 0, value).get() ? CASResponse.OK : CASResponse.EXISTS;
               } catch (InterruptedException e) {
                  throw new IllegalStateException(e);
               } catch (ExecutionException e) {
                  throw new IllegalStateException(e);
               }
            } else {
               response = client.cas(stringKey, prev.getCas(), value);
            }
            if (response == CASResponse.OK) {
               return (V) prev.getValue();
            }
         }
      }

      @Override
      public boolean remove(K key) {
         try {
            return client.delete(key.toString()).get();
         } catch (InterruptedException e) {
            throw new IllegalStateException(e);
         } catch (ExecutionException e) {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public V getAndRemove(K key) {
         String stringKey = key.toString();
         for (;;) {
            CASValue<Object> prev = client.gets(stringKey);
            if (prev.getValue() == null) {
               return null;
            } else {
               if (client.cas(stringKey, prev.getCas(), null) == CASResponse.OK) {
                  return (V) prev.getValue();
               }
            }
         }
      }

      @Override
      public void clear() {
         try {
            client.flush().get();
         } catch (InterruptedException e) {
            throw new IllegalStateException(e);
         } catch (ExecutionException e) {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public boolean putIfAbsent(K key, V value) {
         try {
            return client.add(key.toString(), 0, value).get();
         } catch (InterruptedException e) {
            throw new IllegalStateException(e);
         } catch (ExecutionException e) {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public boolean remove(K key, V oldValue) {
         String stringKey = key.toString();
         for (;;) {
            CASValue<Object> prev = client.gets(stringKey);
            if (oldValue.equals(prev.getValue())) {
               if (client.cas(stringKey, prev.getCas(), null) == CASResponse.OK) {
                  return true;
               }
            } else {
               return false;
            }
         }
      }

      @Override
      public boolean replace(K key, V oldValue, V newValue) {
         String stringKey = key.toString();
         for (;;) {
            CASValue<Object> prev = client.gets(stringKey);
            if (oldValue.equals(prev.getValue())) {
               if (client.cas(stringKey, prev.getCas(), newValue) == CASResponse.OK) {
                  return true;
               }
            } else {
               return false;
            }
         }
      }

      @Override
      public boolean replace(K key, V value) {
         try {
            return client.replace(key.toString(), 0, value).get();
         } catch (InterruptedException e) {
            throw new IllegalStateException(e);
         } catch (ExecutionException e) {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public V getAndReplace(K key, V value) {
         String stringKey = key.toString();
         CASResponse response;
         for (;;) {
            CASValue<Object> prev = client.gets(stringKey);
            if (prev.getValue() == null) {
               return null;
            } else {
               response = client.cas(stringKey, prev.getCas(), value);
            }
            if (response == CASResponse.OK) {
               return (V) prev.getValue();
            }
         }
      }
   }
}
