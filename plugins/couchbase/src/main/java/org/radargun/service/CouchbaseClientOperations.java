package org.radargun.service;

import java.nio.charset.StandardCharsets;

import com.couchbase.client.java.document.ByteArrayDocument;
import org.radargun.traits.BasicOperations;

public class CouchbaseClientOperations implements BasicOperations {

   protected final CouchbaseClientService service;
   private final CouchbaseCacheAdapter adapter = new CouchbaseCacheAdapter();

   public CouchbaseClientOperations(CouchbaseClientService service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName) {
      return adapter;
   }

   private class CouchbaseCacheAdapter<K, V> implements BasicOperations.Cache<K, V> {

      private String keyAsString(Object object) {
         if (object instanceof String) {
            return String.valueOf(object);
         } else if (object instanceof byte[]) {
            return new String((byte[]) object, StandardCharsets.UTF_8);
         }

         throw new IllegalArgumentException("I don't know hot to decode: " + object + ", class type: " + object.getClass());
      }

      private byte[] valueAsByteArray(Object object) {
         if (object instanceof String) {
            return ((String) object).getBytes();
         } else if (object instanceof byte[]) {
            return (byte[]) object;
         }

         throw new IllegalArgumentException("I don't know hot to decode: " + object + ", class type: " + object.getClass());
      }

      @Override
      public V get(K key) {
         ByteArrayDocument document = service.bucket.get(keyAsString(key), ByteArrayDocument.class);
         return (V) document.content();
      }

      @Override
      public boolean containsKey(K key) {
         return service.bucket.exists(keyAsString(key));
      }

      @Override
      public void put(K key, V value) {
         ByteArrayDocument doc = ByteArrayDocument.create(keyAsString(key), valueAsByteArray(value));
         service.bucket.upsert(doc);
      }

      @Override
      public V getAndPut(K key, V value) {
         throw new UnsupportedOperationException("Couchbase does not support get and put as single operation.");
      }

      @Override
      public boolean remove(K key) {
         try {
            service.bucket.remove(keyAsString(key));
            return true;
         } catch(Exception e) {
            return false;
         }
      }

      @Override
      public V getAndRemove(K key) {
         throw new UnsupportedOperationException("Couchbase does not support get and remove as single operation.");
      }

      @Override
      public void clear() {
         service.bucket.bucketManager().flush();
      }
   }
}
