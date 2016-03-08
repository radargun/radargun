package org.radargun.traits;

import java.util.Map;
import java.util.Set;

import org.radargun.Operation;

/**
 * Partially taken from JSR-107 Cache
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Operations using multiple key-value pairs.")
public interface BulkOperations {
   String TRAIT = BulkOperations.class.getSimpleName();
   Operation GET_ALL_NATIVE = Operation.register(TRAIT + ".GetAll.Native");
   Operation GET_ALL_ASYNC = Operation.register(TRAIT + ".GetAll.Async");
   Operation PUT_ALL_NATIVE = Operation.register(TRAIT + ".PutAll.Native");
   Operation PUT_ALL_ASYNC = Operation.register(TRAIT + ".PutAll.Async");
   Operation REMOVE_ALL_NATIVE = Operation.register(TRAIT + ".RemoveAll.Native");
   Operation REMOVE_ALL_ASYNC = Operation.register(TRAIT + ".RemoveAll.Async");

   /**
    * The cache may provide native implementation of bulk get or it may be simulated
    * with multiple asynchronous operations. Native version should be preferred, unless
    * preferAsync is set to true.
    */
   <K, V> Cache<K, V> getCache(String cacheName, boolean preferAsync);

   interface Cache<K, V> {
      /**
       * Gets a collection of entries from the Cache, returning them as
       * {@link Map} of the values associated with the set of keys requested.
       *
       * @return A map of entries that were found for the given keys. Keys not found
       * in the cache are not in the returned map.
       */
      Map<K, V> getAll(Set<K> keys);

      /**
       * Copies all of the entries from the specified map to the Cache.
       * <p>
       * The effect of this call is equivalent to that of calling
       * put(k, v) on this cache once for each mapping
       * from key <tt>k</tt> to value <tt>v</tt> in the specified map.
       * <p>
       * The order in which the individual puts occur is undefined.
       * <p>
       * The behavior of this operation is undefined if entries in the cache
       * corresponding to entries in the map are modified or removed while this
       * operation is in progress. or if map is modified while the operation is in
       * progress.
       * <p>
       * It is not required that all puts appear atomically.
       */
      void putAll(Map<K, V> entries);

      /**
       * Removes entries for the specified keys.
       * <p>
       * The order in which the individual entries are removed is undefined.
       */
      void removeAll(Set<K> keys);
   }
}
