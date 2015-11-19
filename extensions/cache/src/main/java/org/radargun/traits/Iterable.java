package org.radargun.traits;

import java.io.Closeable;
import java.util.Iterator;
import java.util.Map;

import org.radargun.Operation;

/**
 * Allows to iterate through the whole container.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Trait(doc = "Provides way to iterate through all entries.")
public interface Iterable {
   Operation GET_ITERATOR = Operation.register(Iterable.class.getSimpleName() + ".GetIterator");
   Operation HAS_NEXT = Operation.register(Iterable.class.getSimpleName() + ".HasNext");
   Operation NEXT = Operation.register(Iterable.class.getSimpleName() + ".Next");
   Operation REMOVE = Operation.register(Iterable.class.getSimpleName() + ".Remove");
   // used to denote time used iterating the whole set
   Operation FULL_LOOP = Operation.register(Iterable.class.getSimpleName() + ".FullLoop");

   /**
    * Returns iterator used to traverse the set of data in the target container.
    * Implementation of {@link java.util.Iterator#remove()} is optional.
    * After the Iterator is no longer required, it must be {@link java.io.Closeable#close() closed}.
    * Note: Stage should always load the filter through plugin class loader. Also, some plugins
    * may have trouble serializing generic filter class.
    *
    * @param containerName Name of the cache, db table etc...
    * @param filter If null, all entries should be returned.
    * @param <K> Type of the key
    * @param <V> Type of the value
    * @return
    */
   <K, V> CloseableIterator<Map.Entry<K, V>> getIterator(String containerName, Filter<K, V> filter);

   /**
    * Returns iterator used to traverse the set of data in the target container.
    * Implementation of {@link java.util.Iterator#remove()} is optional.
    * After the Iterator is no longer required, it must be {@link java.io.Closeable#close() closed}.
    * Note: Stage should always load the filter/converter through plugin class loader. Also, some plugins
    * may have trouble serializing generic filter/converter class.
    *
    * @param containerName Name of the cache, db table etc...
    * @param filter If null, all entries should be returned.
    * @param converter Must not be null
    * @param <K> Type of the key
    * @param <V> Type of the value
    * @param <T> Type of the returned value
    * @return
    */
   <K, V, T> CloseableIterator<T> getIterator(String containerName, Filter<K, V> filter, Converter<K, V, T> converter);

   interface CloseableIterator<T> extends Iterator<T>, Closeable {}

   /**
    * Filter that tells whether an entry should be included in the traversed set.
    * @param <K>
    * @param <V>
    */
   interface Filter<K, V> {
      boolean accept(K key, V value);
   }

   interface Converter<K, V, T> {
      T convert(K key, V value);
   }
}
