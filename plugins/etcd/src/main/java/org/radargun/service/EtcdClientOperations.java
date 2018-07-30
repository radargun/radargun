package org.radargun.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.coreos.jetcd.KV;
import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.data.KeyValue;
import com.coreos.jetcd.kv.DeleteResponse;
import com.coreos.jetcd.kv.GetResponse;
import com.coreos.jetcd.kv.PutResponse;
import com.coreos.jetcd.options.DeleteOption;
import com.coreos.jetcd.options.GetOption;
import com.coreos.jetcd.options.PutOption;
import org.radargun.traits.BasicOperations;

public class EtcdClientOperations implements BasicOperations {

   public static final PutOption GET_PUT_OPTION = PutOption.newBuilder().withPrevKV().build();
   public static final DeleteOption GET_DELETE_OPTION = DeleteOption.newBuilder().withPrevKV(true).build();
   public static final GetOption DEFAULT_GET_OPTION = GetOption.newBuilder().withSerializable(true).build();

   protected final EtcdClientService service;

   public EtcdClientOperations(EtcdClientService service) {
      this.service = service;
   }

   @Override
   public <K, V> Cache<K, V>  getCache(String cacheName) {
      return new EtcdCacheAdapter(service.kvClient);
   }

   private class EtcdCacheAdapter<K, V> implements BasicOperations.Cache<K, V>  {

      private KV kvClient;

      public EtcdCacheAdapter(KV kvClient) {
         this.kvClient = kvClient;
      }

      @Override
      public V get(K key) {
         Optional<KeyValue> value = this.retrieveValueFrom(key);
         return retrieveBytes(value);
      }

      @Override
      public boolean containsKey(K key) {
         Optional<KeyValue> value = this.retrieveValueFrom(key);
         return value.isPresent();
      }

      @Override
      public void put(K key, V value) {
         try {
            kvClient.put(createByteSequence(key), createByteSequence(value)).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to put the cache value", e);
         }
      }

      @Override
      public V getAndPut(K key, V value) {
         PutResponse response;
         try {
            response = kvClient.put(createByteSequence(key), createByteSequence(value), GET_PUT_OPTION).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to put the cache value", e);
         }

         if (response.hasPrevKv()) {
            return (V) response.getPrevKv().getValue().getBytes();
         } else {
            return null;
         }
      }

      @Override
      public boolean remove(K key) {
         DeleteResponse response;
         try {
            response = kvClient.delete(createByteSequence(key)).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to delete the cache", e);
         }
         return response.getDeleted() > 0;
      }

      @Override
      public V getAndRemove(K key) {
         DeleteResponse response;
         try {
            response = kvClient.delete(createByteSequence(key), GET_DELETE_OPTION).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to delete the cache", e);
         }

         List<KeyValue> prevKvs = response.getPrevKvs();
         if (prevKvs != null && prevKvs.size() > 0) {
            return (V) prevKvs.get(0).getValue().getBytes();
         } else {
            return null;
         }
      }

      @Override
      public void clear() {
         throw new UnsupportedOperationException("Clearing etcd is not supported.");
      }

      private V retrieveBytes(Optional<KeyValue> optionalKeyValue) {
         if (!optionalKeyValue.isPresent()) {
            throw new NullPointerException("Cache value cannot be null");
         }
         return (V) optionalKeyValue.get().getValue().getBytes();
      }

      private Optional<KeyValue> retrieveValueFrom(K key) {
         GetResponse response;
         try {
            response = kvClient.get(createByteSequence(key), DEFAULT_GET_OPTION).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to retrieve the cache value", e);
         }
         KeyValue keyValue = null;
         List<KeyValue> values = response.getKvs();
         if (values != null && values.size() > 0) {
            keyValue = values.get(0);
         }
         return Optional.ofNullable(keyValue);
      }

      private ByteSequence createByteSequence(Object object) {
         ByteSequence etcdKey;
         if (object instanceof String) {
            etcdKey = ByteSequence.fromString((String) object);
         } else if (object instanceof byte[]) {
            etcdKey = ByteSequence.fromBytes((byte[]) object);
         } else {
            throw new IllegalArgumentException("I don't know hot to decode: " + object + ", class type: " + object.getClass());
         }
         return etcdKey;
      }
   }
}
