package org.radargun.service;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.util.CloseableIterable;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.iteration.EntryIterable;

/**
 * Infinispan and JDG are only partially binary compatible
 */
public class JDG63Iterable extends InfinispanIterable {
   public JDG63Iterable(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public <K, V> CloseableIterator<Map.Entry<K, V>> getIterator(String cacheName, Filter<K, V> filter) {
      if (trace) log.tracef("Retrieving iterator for cache %s using filter %s", cacheName, filter);
      AdvancedCache<K, V> cache = (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache();
      EntryIterable<K, V> iterable = cache.filterEntries(wrap(filter));
      return new CloseableIteratorImpl(iterable, new EntryOutConverter());
   }

   @Override
   public <K, V, T> CloseableIterator<T> getIterator(String cacheName, Filter<K, V> filter, Converter<K, V, T> converter) {
      if (trace)
         log.tracef("Retrieving iterator for cache %s using filter %s and converter %s", cacheName, filter, converter);
      AdvancedCache<K, V> cache = (AdvancedCache<K, V>) service.getCache(cacheName).getAdvancedCache();
      EntryIterable<K, V> iterable = cache.filterEntries(wrap(filter));
      return new CloseableIteratorImpl(iterable.converter(wrap(converter)), new ValueOutConverter());
   }

   private interface OutConverter<T> {
      T convert(CacheEntry entry);
   }

   private static class EntryOutConverter implements OutConverter<Map.Entry<Object, Object>> {
      @Override
      public Map.Entry<Object, Object> convert(CacheEntry entry) {
         return entry;
      }
   }

   private static class ValueOutConverter implements OutConverter<Object> {
      @Override
      public Object convert(CacheEntry entry) {
         return entry.getValue();
      }
   }

   private static class CloseableIteratorImpl<T> implements CloseableIterator<T> {
      private final CloseableIterable<CacheEntry> iterable;
      private final Iterator<CacheEntry> iterator;
      private final OutConverter<T> outConverter;

      public CloseableIteratorImpl(CloseableIterable<CacheEntry> iterable, OutConverter<T> outConverter) {
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
