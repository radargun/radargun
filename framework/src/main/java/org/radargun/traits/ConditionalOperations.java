package org.radargun.traits;

/**
 * Partially taken from JSR-107 Cache
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Operations that are executed depending on the current value in the cache")
public interface ConditionalOperations {

   <K, V> Cache<K, V> getCache(String cacheName);

   interface Cache<K, V> {
      /**
       * Atomically associates the specified key with the given value if it is
       * not already associated with a value.
       * <p>
       * This is equivalent to:
       * <pre><code>
       * if (!cache.containsKey(key)) {}
       * cache.put(key, value);
       * return true;
       * } else {
       * return false;
       * }
       * </code></pre>
       * except that the action is performed atomically.
       *
       * @return true if a value was set.
       */
      boolean putIfAbsent(K key, V value);

      /**
       * Atomically removes the mapping for a key only if currently mapped to the
       * given value.
       * <p>
       * This is equivalent to:
       * <pre><code>
       * if (cache.containsKey(key) &amp;&amp; equals(cache.get(key), oldValue) {
       * cache.remove(key);
       * return true;
       * } else {
       * return false;
       * }
       * </code></pre>
       * except that the action is performed atomically.
       *
       * @return returns false if there was no matching key
       */
      boolean remove(K key, V oldValue);

      /**
       * Atomically replaces the entry for a key only if currently mapped to a
       * given value.
       * <p>
       * This is equivalent to:
       * <pre><code>
       * if (cache.containsKey(key) &amp;&amp; equals(cache.get(key), oldValue)) {
       * cache.put(key, newValue);
       * return true;
       * } else {
       * return false;
       * }
       * </code></pre>
       * except that the action is performed atomically.
       *
       * @return <tt>true</tt> if the value was replaced
       */
      boolean replace(K key, V oldValue, V newValue);

      /**
       * Atomically replaces the entry for a key only if currently mapped to some
       * value.
       * <p>
       * This is equivalent to
       * <pre><code>
       * if (cache.containsKey(key)) {
       * cache.put(key, value);
       * return true;
       * } else {
       * return false;
       * }</code></pre>
       * except that the action is performed atomically.
       *
       * @return <tt>true</tt> if the value was replaced
       */
      boolean replace(K key, V value);

      /**
       * Atomically replaces the value for a given key if and only if there is a
       * value currently mapped by the key.
       * <p>
       * This is equivalent to
       * <pre><code>
       * if (cache.containsKey(key)) {
       * V oldValue = cache.get(key);
       * cache.put(key, value);
       * return oldValue;
       * } else {
       * return null;
       * }
       * </code></pre>
       * except that the action is performed atomically.
       *
       * @return the previous value associated with the specified key, or
       * <tt>null</tt> if there was no mapping for the key.
       */
      V getAndReplace(K key, V value);
   }
}
