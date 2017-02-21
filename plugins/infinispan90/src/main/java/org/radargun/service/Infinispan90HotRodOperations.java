package org.radargun.service;

import java.io.InputStream;
import java.io.OutputStream;

import org.infinispan.client.hotrod.StreamingRemoteCache;
import org.radargun.traits.StreamingOperations;

public class Infinispan90HotRodOperations extends Infinispan72HotRodOperations implements StreamingOperations {

   public Infinispan90HotRodOperations(InfinispanHotrodService service) {
      super(service);
   }

   public <K, V> StreamingCache<K> getStreamingCache(String cacheName) {
      HotRodCache<K, V> cache = super.getCache(cacheName);
      return new StreamingHotRodCache<K, V>(cache.noReturn.streaming());
   }

   protected class StreamingHotRodCache<K, V> implements StreamingOperations.StreamingCache<K> {

      protected final StreamingRemoteCache<K> streamingCache;

      public StreamingHotRodCache(StreamingRemoteCache<K> streamingCache) {
         this.streamingCache = streamingCache;
      }

      public InputStream getViaStream(K key) {
         return streamingCache.get(key);
      }

      public OutputStream putViaStream(K key) {
         return streamingCache.put(key);
      }
   }
}
