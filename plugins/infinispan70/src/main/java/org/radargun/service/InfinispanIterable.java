package org.radargun.service;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.commons.util.CloseableIterable;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.filter.KeyValueFilter;
import org.infinispan.iteration.EntryIterable;
import org.infinispan.metadata.Metadata;
import org.radargun.filters.AllFilter;
import org.radargun.traits.Iterable;

/**
 * Implements iteration through all entries in cache.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanIterable implements Iterable {
   protected static final Log log = LogFactory.getLog(Iterable.class);
   protected static final boolean trace = log.isTraceEnabled();

   protected final InfinispanEmbeddedService service;

   public InfinispanIterable(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public <K, V> CloseableIterator<Map.Entry<K, V>> getIterator(String cacheName, Filter<K, V> filter) {
      if (trace) log.tracef("Retrieving iterator for cache %s using filter %s", cacheName, filter);
      AdvancedCache<K, V> cache = (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache();
      EntryIterable<K, V> iterable = cache.filterEntries(wrap(filter));
      return new CloseableIteratorImpl(iterable, new EntryOutConverter<K, V>());
   }

   @Override
   public <K, V, T> CloseableIterator<T> getIterator(String cacheName, Filter<K, V> filter, Converter<K, V, T> converter) {
      if (trace) log.tracef("Retrieving iterator for cache %s using filter %s and converter %s", cacheName, filter, converter);
      AdvancedCache<K, V> cache = (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache();
      EntryIterable<K, V> iterable = cache.filterEntries(wrap(filter));
      return new CloseableIteratorImpl(iterable.converter(wrap(converter)), new ValueOutConverter<K, T>());
   }

   protected <K, V, T> org.infinispan.filter.Converter<K, V, T> wrap(Converter<K, V, T> converter) {
      return new ConverterWrapper<>(converter);
   }

   protected <K, V> KeyValueFilter<K, V> wrap(Filter<K, V> filter) {
      if (filter == null) {
         return AllFilter.INSTANCE;
      } else {
         return new KeyValueFilterWrapper<K, V>(filter);
      }
   }

   /**
    * Adapts RadarGun filter to Infinispan filter
    */
   private static class KeyValueFilterWrapper<K, V> implements KeyValueFilter<K, V>, Serializable {
      private final Filter<K, V> filter;

      private KeyValueFilterWrapper(Filter<K, V> filter) {
         this.filter = filter;
      }

      @Override
      public boolean accept(K key, V value, Metadata metadata) {
         return filter.accept(key, value);
      }
   }

   /**
    * Adapts RadarGun converter to Infinispan converter
    */
   private static class ConverterWrapper<K, V, T> implements org.infinispan.filter.Converter<K, V, T>, Serializable {
      private final Converter<K, V, T> converter;

      public ConverterWrapper(Converter<K, V, T> converter) {
         this.converter = converter;
      }

      @Override
      public T convert(K key, V value, Metadata metadata) {
         return converter.convert(key, value);
      }
   }

   /**
    * Adapts Infinispan iteration output to RadarGun iteration output
    */
   private interface OutConverter<K, V, T> {
      T convert(CacheEntry<K, V> entry);
   }

   private static class EntryOutConverter<K, V> implements OutConverter<K, V, Map.Entry<K, V>> {
      @Override
      public Map.Entry<K, V> convert(CacheEntry<K, V> entry) {
         return entry;
      }
   }

   private static class ValueOutConverter<K, T> implements OutConverter<K, T, T> {
      @Override
      public T convert(CacheEntry<K, T> entry) {
         return entry.getValue();
      }
   }

   /**
    * Implements the iterator
    */
   private static class CloseableIteratorImpl<K, V, T> implements CloseableIterator<T> {
      private final CloseableIterable<CacheEntry<K, V>> iterable;
      private final Iterator<CacheEntry<K, V>> iterator;
      private final OutConverter<K, V, T> outConverter;

      public CloseableIteratorImpl(CloseableIterable<CacheEntry<K, V>> iterable, OutConverter<K, V, T> outConverter) {
         this.iterable = iterable;
         this.outConverter = outConverter;
         this.iterator = iterable.iterator();
      }

      @Override
      public void close() throws IOException {
         iterable.close();
      }

      @Override
      public boolean hasNext() {
         return iterator.hasNext();
      }

      @Override
      public T next() {
         return outConverter.convert(iterator.next());
      }

      @Override
      public void remove() {
         iterator.remove();
      }
   }
}