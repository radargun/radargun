package org.radargun.traits;

import java.io.InputStream;
import java.io.OutputStream;

import org.radargun.Operation;

/**
 * Basic streaming operations
 * 
 * @author zhostasa
 *
 */
@Trait(doc = "Cache streaming operations")
public interface StreamingOperations {
   String TRAIT = StreamingOperations.class.getSimpleName();
   Operation GET = Operation.register(TRAIT + ".Get");
   Operation PUT = Operation.register(TRAIT + ".Put");

   <K, V> StreamingCache<K> getStreamingCache(String cacheName);

   interface StreamingCache<K> {

      /**
       * Gets InputStream to value stored under the key in cache
       * 
       * @param key
       * @return InputStream or null if key is not in cache
       */
      InputStream getViaStream(K key);

      /**
       * Gets OutputStream to store value into under the key
       * 
       * @param key
       * @return OutputStream
       */
      OutputStream putViaStream(K key);
   }
}
