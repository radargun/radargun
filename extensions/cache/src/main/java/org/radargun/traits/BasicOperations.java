package org.radargun.traits;

import org.radargun.Operation;

/**
 * Partially taken from JSR-107 Cache specification
 */
@Trait(doc = "The most basic operations on cache.")
public interface BasicOperations {
   String TRAIT = BasicOperations.class.getSimpleName();
   Operation GET = Operation.register(TRAIT + ".Get");
   Operation CONTAINS_KEY = Operation.register(TRAIT + ".ContainsKey");
   Operation PUT = Operation.register(TRAIT + ".Put");
   Operation GET_AND_PUT = Operation.register(TRAIT + ".GetAndPut");
   Operation REMOVE = Operation.register(TRAIT + ".Remove");
   Operation GET_AND_REMOVE = Operation.register(TRAIT + ".GetAndRemove");
   Operation CLEAR = Operation.register(TRAIT + ".Clear");

   <K, V> Cache<K, V> getCache(String cacheName);

   interface Cache<K, V> {
      /**
       * Gets an entry from the cache.
       *
       * @return the element, or null, if it does not exist.
       */
      V get(K key);

      /**
       * Determines if the Cache contains an entry for the specified key.
       *
       * @return <tt>true</tt> if this map contains a mapping for the specified key
       * @see java.util.Map#containsKey(Object)
       */
      boolean containsKey(K key);

      /**
       * Associates the specified value with the specified key in the cache.
       * <p>
       * If the Cache previously contained a mapping for the key, the old
       * value is replaced by the specified value. (A cache <tt>c</tt> is said to
       * contain a mapping for a key <tt>k</tt> if and only if c.containsKey(k)
       * would return <tt>true</tt>.)
       *
       * @see java.util.Map#put(Object, Object)
       */
      void put(K key, V value);

      /**
       * Associates the specified value with the specified key in this cache,
       * returning an existing value if one existed.
       * <p>
       * If the cache previously contained a mapping for the key, the old value
       * is replaced by the specified value. (A cache <tt>c</tt> is said to contain
       * a mapping for a key <tt>k</tt> if and only if c.containsKey(k) would return
       * <tt>true</tt>.)
       * <p>
       * The previous value is returned, or null if there was no value associated
       * with the key previously.
       *
       * @return the value associated with the key at the start of the operation or
       * null if none was associated.
       */
      V getAndPut(K key, V value);

      /**
       * Removes the mapping for a key from this cache if it is present.
       *
       * <p>Returns <tt>true</tt> if this cache previously associated the key,
       * or <tt>false</tt> if the cache contained no mapping for the key.
       * <p>
       * The cache will not contain a mapping for the specified key once the
       * call returns.
       *
       * @return returns false if there was no matching key
       */
      boolean remove(K key);

      /**
       * Atomically removes the entry for a key only if currently mapped to some
       * value.
       * <p>
       * This is equivalent to:
       * <pre><code>
       * if (cache.containsKey(key)) {
       * V oldValue = cache.get(key);
       * cache.remove(key);
       * return oldValue;
       * } else {
       * return null;
       * }
       * </code></pre>
       * except that the action is performed atomically.
       *
       * @return the value if one existed or null if no mapping existed for this key
       */
      V getAndRemove(K key);

      /**
       * Drops all mappings from the cache. If local is set to true, drop entries only on local node.
       * The mappings can be removed in any order, the change does not have to be atomic and no events have
       * to be fired when removing the entry.
       */
      void clear();
   }
}
