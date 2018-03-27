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
   public Cache<byte[], byte[]> getCache(String cacheName) {
      return new EtcdCacheAdapter(service.kvClient);
   }

   private class EtcdCacheAdapter implements BasicOperations.Cache<byte[], byte[]> {

      private KV kvClient;

      public EtcdCacheAdapter(KV kvClient) {
         this.kvClient = kvClient;
      }

      @Override
      public byte[] get(byte[] key) {
         Optional<KeyValue> value = this.retrieveValueFrom(key);
         return retrieveBytes(value);
      }

      @Override
      public boolean containsKey(byte[] key) {
         Optional<KeyValue> value = this.retrieveValueFrom(key);
         return value.isPresent();
      }

      @Override
      public void put(byte[] key, byte[] value) {
         try {
            kvClient.put(ByteSequence.fromBytes(key), ByteSequence.fromBytes(value)).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to put the cache value", e);
         }
      }

      @Override
      public byte[] getAndPut(byte[] key, byte[] value) {
         PutResponse response;
         try {
            response = kvClient.put(ByteSequence.fromBytes(key), ByteSequence.fromBytes(value), GET_PUT_OPTION).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to put the cache value", e);
         }

         if (response.hasPrevKv()) {
            return response.getPrevKv().getValue().getBytes();
         } else {
            return null;
         }
      }

      @Override
      public boolean remove(byte[] key) {
         DeleteResponse response;
         try {
            response = kvClient.delete(ByteSequence.fromBytes(key)).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to delete the cache", e);
         }
         return response.getDeleted() > 0;
      }

      @Override
      public byte[] getAndRemove(byte[] key) {
         DeleteResponse response;
         try {
            response = kvClient.delete(ByteSequence.fromBytes(key), GET_DELETE_OPTION).get();
         } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Impossible to delete the cache", e);
         }

         List<KeyValue> prevKvs = response.getPrevKvs();
         if (prevKvs != null && prevKvs.size() > 0) {
            return prevKvs.get(0).getValue().getBytes();
         } else {
            return null;
         }
      }

      @Override
      public void clear() {
         throw new UnsupportedOperationException("Clearing etcd is not supported.");
      }

      private byte[] retrieveBytes(Optional<KeyValue> optionalKeyValue) {
         if (!optionalKeyValue.isPresent()) {
            throw new NullPointerException("Cache value cannot be null");
         }
         return optionalKeyValue.get().getValue().getBytes();
      }

      private Optional<KeyValue> retrieveValueFrom(byte[] key) {
         GetResponse response;
         try {
            response = kvClient.get(ByteSequence.fromBytes(key), DEFAULT_GET_OPTION).get();
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
   }
}
