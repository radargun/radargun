package org.radargun.util;

import org.radargun.traits.Debugable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Matej Cimbora
 */
public class CacheTraitRepository extends CoreTraitRepository {

   public static Map<Class<?>, Object> getAllTraits() {
      Map<Class<?>, Object> traitMap = new HashMap<>(CoreTraitRepository.getAllTraits());
      ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap();
      traitMap.put(org.radargun.traits.BasicOperations.class, new BasicOperations(new BasicOperationsCache(concurrentHashMap)));
      traitMap.put(org.radargun.traits.BulkOperations.class, new BulkOperations(new BulkOperationsCache(concurrentHashMap)));
      traitMap.put(org.radargun.traits.ConditionalOperations.class, new ConditionalOperations(new ConditionalOperations.ConditionalOperationsCache(concurrentHashMap)));
      traitMap.put(org.radargun.traits.Iterable.class, new Iterable<>(concurrentHashMap));
      traitMap.put(org.radargun.traits.CacheInformation.class, new CacheInformation(new CacheInformation.Cache(concurrentHashMap)));
      traitMap.put(org.radargun.traits.MapReducer.class, new MapReducer(concurrentHashMap));
      traitMap.put(org.radargun.traits.DistributedTaskExecutor.class, new DistributedTaskExecutor(concurrentHashMap));
      traitMap.put(org.radargun.traits.TopologyHistory.class, new TopologyHistory());
      traitMap.put(org.radargun.traits.Debugable.class, new Debuggable());
      traitMap.put(org.radargun.traits.CacheListeners.class, new CacheListeners());
      return traitMap;
   }

   public static class BasicOperations implements org.radargun.traits.BasicOperations {

      private final BasicOperationsCache cache;

      public BasicOperations(BasicOperationsCache cache) {
         this.cache = cache;
      }

      @Override
      public <K, V> Cache<K, V> getCache(String cacheName) {
         return cache;
      }
   }

   public static class BasicOperationsCache<K, V> implements BasicOperations.Cache<K, V>, Wrappable {

      protected ConcurrentHashMap<K, V> cache;

      public BasicOperationsCache() {
         this.cache = new ConcurrentHashMap<>();
      }

      public BasicOperationsCache(ConcurrentHashMap<K, V> cache) {
         this.cache = cache;
      }

      @Override
      public Object get(Object key) {
         return cache.get(key);
      }

      @Override
      public boolean containsKey(Object key) {
         return cache.containsKey(key);
      }

      @Override
      public void put(Object key, Object value) {
         cache.put((K) key, (V) value);
      }

      @Override
      public Object getAndPut(Object key, Object value) {
         return cache.put((K) key, (V) value);
      }

      @Override
      public boolean remove(Object key) {
         return cache.remove(key) != null;
      }

      @Override
      public Object getAndRemove(Object key) {
         return cache.remove(key);
      }

      @Override
      public void clear() {
         cache.clear();
      }

      public int size() {
         return cache.size();
      }

      @Override
      public TxResource wrap() {
         return new BasicOperationsCacheTxWrapper<>(this);
      }
   }

   public static class BasicOperationsCacheTxWrapper<K, V> extends BasicOperationsCache<K, V> implements TxResource {

      private final BasicOperationsCache<K, V> localCache;
      private ConcurrentHashMap<K, V> operationsBuffer;

      public BasicOperationsCacheTxWrapper(BasicOperationsCache<K, V> cache) {
         super(cache.cache);
         this.localCache = cache;
      }

      @Override
      public Object get(Object key) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         return operationsBuffer.get(key);
      }

      @Override
      public boolean containsKey(Object key) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         return operationsBuffer.containsKey(key);
      }

      @Override
      public void put(Object key, Object value) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         operationsBuffer.put((K) key, (V) value);
      }

      @Override
      public Object getAndPut(Object key, Object value) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         V result = operationsBuffer.get(key);
         operationsBuffer.put((K) key, (V) value);
         return result;
      }

      @Override
      public boolean remove(Object key) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         return operationsBuffer.remove(key) != null;
      }

      @Override
      public Object getAndRemove(Object key) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         V result = operationsBuffer.get(key);
         operationsBuffer.remove(key);
         return result;
      }

      @Override
      public void clear() {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         operationsBuffer.clear();
      }

      @Override
      public void begin() {
         operationsBuffer = new ConcurrentHashMap<>(localCache.cache);
      }

      @Override
      public void commit() {
         localCache.cache.putAll(operationsBuffer);
         operationsBuffer = null;
      }

      @Override
      public void rollback() {
         operationsBuffer = null;
      }
   }

   public static class BulkOperations implements org.radargun.traits.BulkOperations {

      private final BulkOperationsCache cache;

      public BulkOperations(BulkOperationsCache cache) {
         this.cache = cache;
      }

      @Override
      public <K, V> Cache<K, V> getCache(String cacheName, boolean preferAsync) {
         return cache;
      }
   }

   public static class BulkOperationsCache<K, V> implements BulkOperations.Cache<K, V>, Wrappable {

      protected ConcurrentHashMap<K, V> cache;

      public BulkOperationsCache() {
         this.cache = new ConcurrentHashMap<>();
      }

      public BulkOperationsCache(ConcurrentHashMap<K, V> cache) {
         this.cache = cache;
      }

      @Override
      public Map<K, V> getAll(Set<K> keys) {
         Map<K, V> result = new HashMap<>(cache);
         result.keySet().retainAll(keys);
         return result;
      }

      @Override
      public void putAll(Map<K, V> entries) {
         cache.putAll(entries);
      }

      @Override
      public void removeAll(Set<K> keys) {
         cache.keySet().removeAll(keys);
      }

      public int size() {
         return cache.size();
      }

      @Override
      public TxResource wrap() {
         return new BulkOperationsCacheTxWrapper<>(this);
      }
   }

   public static class BulkOperationsCacheTxWrapper<K, V> extends BulkOperationsCache<K, V> implements TxResource {

      private final BulkOperationsCache localCache;
      private ConcurrentHashMap<K, V> operationsBuffer;

      public BulkOperationsCacheTxWrapper(BulkOperationsCache cache) {
         this.localCache = cache;
      }

      @Override
      public Map<K, V> getAll(Set<K> keys) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         Map<K, V> result = new HashMap<>(operationsBuffer);
         result.keySet().retainAll(keys);
         return result;
      }

      @Override
      public void putAll(Map<K, V> entries) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         operationsBuffer.putAll(entries);
      }

      @Override
      public void removeAll(Set<K> keys) {
         if (operationsBuffer == null) {
            throw new IllegalStateException("Tx has not begun yet");
         }
         operationsBuffer.keySet().removeAll(keys);
      }

      public int size() {
         return localCache.size();
      }

      @Override
      public void begin() {
         operationsBuffer = new ConcurrentHashMap<>(localCache.cache);
      }

      @Override
      public void commit() {
         localCache.cache.putAll(operationsBuffer);
         operationsBuffer = null;
      }

      @Override
      public void rollback() {
         operationsBuffer = null;
      }
   }

   // TODO just a dummy implementation to satisfy mandatory dependencies, needs to work with existing cache instances (BasicOperationsCache etc)
   public static class CacheInformation implements org.radargun.traits.CacheInformation {

      private static final String DEFAULT_CACHE_NAME = "default";

      private Cache cache;

      public CacheInformation(Cache cache) {
         this.cache = cache;
      }

      @Override
      public String getDefaultCacheName() {
         return DEFAULT_CACHE_NAME;
      }

      @Override
      public Collection<String> getCacheNames() {
         return Arrays.asList(DEFAULT_CACHE_NAME);
      }

      @Override
      public Cache getCache(String cacheName) {
         return cache;
      }

      private static class Cache implements org.radargun.traits.CacheInformation.Cache {

         private ConcurrentHashMap cache;

         public Cache() {
            this.cache = new ConcurrentHashMap();
         }

         public Cache(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public long getOwnedSize() {
            return cache.size();
         }

         @Override
         public long getLocallyStoredSize() {
            return cache.size();
         }

         @Override
         public long getMemoryStoredSize() {
            return cache.size();
         }

         @Override
         public long getTotalSize() {
            return cache.size();
         }

         @Override
         public Map<?, Long> getStructuredSize() {
            Map<String, Long> structuredSizeMap = new HashMap<>();
            structuredSizeMap.put("part", Long.valueOf(cache.size()));
            return structuredSizeMap;
         }

         @Override
         public int getNumReplicas() {
            return 1;
         }

         @Override
         public int getEntryOverhead() {
            return 0;
         }
      }
   }

   public static class ConditionalOperations implements org.radargun.traits.ConditionalOperations {

      private ConditionalOperationsCache cache;

      public ConditionalOperations(ConditionalOperationsCache cache) {
         this.cache = cache;
      }

      @Override
      public <K, V> Cache<K, V> getCache(String cacheName) {
         return cache;
      }

      private static class ConditionalOperationsCache<K, V> implements org.radargun.traits.ConditionalOperations.Cache<K, V> {

         private ConcurrentHashMap<K, V> cache;

         public ConditionalOperationsCache() {
            this.cache = new ConcurrentHashMap<>();
         }

         public ConditionalOperationsCache(ConcurrentHashMap<K, V> cache) {
            this.cache = cache;
         }

         @Override
         public boolean putIfAbsent(K key, V value) {
            return cache.putIfAbsent(key, value) == null;
         }

         // TODO check implementation (chm has a different one)
         @Override
         public boolean remove(K key, V oldValue) {
            return cache.remove(key, oldValue);
         }

         @Override
         public boolean replace(K key, V oldValue, V newValue) {
            return cache.replace(key, oldValue, newValue);
         }

         @Override
         public boolean replace(K key, V value) {
            return cache.replace(key, value) != null;
         }

         @Override
         public V getAndReplace(K key, V value) {
            for (;;) {
               V oldValue = cache.get(key);
               if (oldValue == null) return null;
               if (cache.replace(key, oldValue, value)) return oldValue;
            }
         }
      }
   }

   public static class Iterable<K, V, T> implements org.radargun.traits.Iterable {

      private ConcurrentHashMap<K, V> cache;

      public Iterable(ConcurrentHashMap<K, V> cache) {
         this.cache = cache;
      }

      @Override
      public <K, V> CloseableIterator<Map.Entry<K, V>> getIterator(String containerName, Filter<K, V> filter) {
         return new CloseableIterator<>(cache, null, null);
      }

      @Override
      public <K, V, T> CloseableIterator<T> getIterator(String containerName, Filter<K, V> filter, Converter<K, V, T> converter) {
         return new CloseableIterator<>(cache, null, null);
      }

      private static class CloseableIterator<T> implements org.radargun.traits.Iterable.CloseableIterator<T> {

         private Iterator iterator;

         public CloseableIterator(ConcurrentHashMap cache, Filter filter, Converter converter) {
            this.iterator = cache.entrySet().iterator();
         }

         @Override
         public void close() throws IOException {
         }

         @Override
         public boolean hasNext() {
            return iterator.hasNext();
         }

         @Override
         public T next() {
            return (T) iterator.next();
         }
      }
   }

   public static class TopologyHistory implements org.radargun.traits.TopologyHistory {

      private List<org.radargun.traits.TopologyHistory.Event> topologyChangeHistory = new LinkedList<>();
      private List<org.radargun.traits.TopologyHistory.Event> rehashHistory = new LinkedList<>();
      private List<org.radargun.traits.TopologyHistory.Event> cacheStatusChangeHistory = new LinkedList<>();

      private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
      private Event.EventType lastTopologyChangeHistoryType;
      private Event.EventType lastRehashHistoryType;


      public void triggerHistoryChanges(long delay, long period, TimeUnit timeUnit) {
         executorService.scheduleAtFixedRate(() -> {
            if (lastTopologyChangeHistoryType == null) {
               lastTopologyChangeHistoryType = Event.EventType.START;
            } else if (lastTopologyChangeHistoryType == Event.EventType.START) {
               lastTopologyChangeHistoryType = Event.EventType.END;
            } else if (lastTopologyChangeHistoryType == Event.EventType.END) {
               lastTopologyChangeHistoryType = Event.EventType.START;
            }

            if (lastRehashHistoryType == null) {
               lastRehashHistoryType = Event.EventType.START;
            } else if (lastRehashHistoryType == Event.EventType.START) {
               lastRehashHistoryType = Event.EventType.END;
            } else if (lastRehashHistoryType == Event.EventType.END) {
               lastRehashHistoryType = Event.EventType.START;
            }
            topologyChangeHistory.add(new Event(new Date(), lastTopologyChangeHistoryType, 1, 1));
            rehashHistory.add(new Event(new Date(), lastRehashHistoryType, 1, 1));
            cacheStatusChangeHistory.add(new Event(new Date(), Event.EventType.SINGLE, 1, 1));

         }, delay, period, timeUnit);
      }

      public void stopHistoryChanges() throws InterruptedException {
         executorService.shutdown();
         if (!topologyChangeHistory.isEmpty()) {
            org.radargun.traits.TopologyHistory.Event event = topologyChangeHistory.get(topologyChangeHistory.size() - 1);
            if (event.getType() == org.radargun.traits.TopologyHistory.Event.EventType.START) {
               topologyChangeHistory.add(new Event(new Date(event.getTime().getTime() + 1), org.radargun.traits.TopologyHistory.Event.EventType.END, 1, 1));
            }
         }
         if (!rehashHistory.isEmpty()) {
            org.radargun.traits.TopologyHistory.Event event = rehashHistory.get(rehashHistory.size() - 1);
            if (event.getType() == org.radargun.traits.TopologyHistory.Event.EventType.START) {
               rehashHistory.add(new Event(new Date(event.getTime().getTime() + 1), org.radargun.traits.TopologyHistory.Event.EventType.END, 1, 1));
            }
         }
      }

      @Override
      public List<org.radargun.traits.TopologyHistory.Event> getTopologyChangeHistory(String containerName) {
         return Collections.unmodifiableList(topologyChangeHistory);
      }

      @Override
      public List<org.radargun.traits.TopologyHistory.Event> getRehashHistory(String containerName) {
         return Collections.unmodifiableList(rehashHistory);
      }

      @Override
      public List<org.radargun.traits.TopologyHistory.Event> getCacheStatusChangeHistory(String containerName) {
         return Collections.unmodifiableList(cacheStatusChangeHistory);
      }

      private static class Event extends org.radargun.traits.TopologyHistory.Event {

         private Date time;
         private EventType type;
         private int membersAtStart;
         private int membersAtEnd;

         public Event(Date time, EventType type, int membersAtStart, int membersAtEnd) {
            this.time = time;
            this.type = type;
            this.membersAtStart = membersAtStart;
            this.membersAtEnd = membersAtEnd;
         }

         @Override
         public Date getTime() {
            return time;
         }

         @Override
         public EventType getType() {
            return type;
         }

         @Override
         public int getMembersAtStart() {
            return membersAtStart;
         }

         @Override
         public int getMembersAtEnd() {
            return membersAtEnd;
         }

         @Override
         public org.radargun.traits.TopologyHistory.Event copy() {
            return new Event(time, type, membersAtStart, membersAtEnd);
         }
      }
   }

   public static class MapReducer implements org.radargun.traits.MapReducer {

      private ConcurrentHashMap cache;

      public MapReducer(ConcurrentHashMap cache) {
         this.cache = cache;
      }

      @Override
      public Builder builder(String cacheName) {
         return new Builder(cache);
      }

      @Override
      public boolean supportsResultCacheName() {
         return true;
      }

      @Override
      public boolean supportsIntermediateSharedCache() {
         return true;
      }

      @Override
      public boolean supportsCombiner() {
         return true;
      }

      @Override
      public boolean supportsTimeout() {
         return true;
      }

      @Override
      public boolean supportsDistributedReducePhase() {
         return true;
      }

      private static class Builder implements org.radargun.traits.MapReducer.Builder {

         private ConcurrentHashMap cache;

         public Builder(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder distributedReducePhase(boolean distributedReducePhase) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder useIntermediateSharedCache(boolean useIntermediateSharedCache) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder timeout(long timeout, TimeUnit unit) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder resultCacheName(String resultCacheName) {
            return this;
         }

         @Override
         public Task build() {
            return new Task(cache);
         }

         @Override
         public org.radargun.traits.MapReducer.Builder collator(String collatorFqn, Map collatorParameters) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder combiner(String combinerFqn, Map combinerParameters) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder reducer(String reducerFqn, Map reducerParameters) {
            return this;
         }

         @Override
         public org.radargun.traits.MapReducer.Builder mapper(String mapperFqn, Map mapperParameters) {
            return this;
         }
      }

      private static class Task implements org.radargun.traits.MapReducer.Task {

         private ConcurrentHashMap cache;

         public Task(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public Map execute() {
            return Collections.unmodifiableMap(cache);
         }

         @Override
         public Object executeWithCollator() {
            return cache.size();
         }
      }
   }

   public static class DistributedTaskExecutor implements org.radargun.traits.DistributedTaskExecutor {

      private ConcurrentHashMap cache;

      public DistributedTaskExecutor(ConcurrentHashMap cache) {
         this.cache = cache;
      }

      @Override
      public Builder builder(String cacheName) {
         return new Builder(cache);
      }

      private static class Builder implements org.radargun.traits.DistributedTaskExecutor.Builder {

         private ConcurrentHashMap cache;

         public Builder(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public org.radargun.traits.DistributedTaskExecutor.Builder callable(Callable callable) {
            return this;
         }

         @Override
         public org.radargun.traits.DistributedTaskExecutor.Builder executionPolicy(String executionPolicy) {
            return this;
         }

         @Override
         public org.radargun.traits.DistributedTaskExecutor.Builder failoverPolicy(String failoverPolicy) {
            return this;
         }

         @Override
         public org.radargun.traits.DistributedTaskExecutor.Builder nodeAddress(String nodeAddress) {
            return this;
         }

         @Override
         public Task build() {
            return new Task(cache);
         }
      }

      private static class Task implements org.radargun.traits.DistributedTaskExecutor.Task {

         private ConcurrentHashMap cache;

         public Task(ConcurrentHashMap cache) {
            this.cache = cache;
         }

         @Override
         public List<Future> execute() {
            return Arrays.asList(new Future<Object>() {

               private boolean isCancelled;

               @Override
               public boolean cancel(boolean mayInterruptIfRunning) {
                  isCancelled = true;
                  return isCancelled;
               }

               @Override
               public boolean isCancelled() {
                  return isCancelled;
               }

               @Override
               public boolean isDone() {
                  return true;
               }

               @Override
               public Object get() throws InterruptedException, ExecutionException {
                  return Collections.unmodifiableMap(cache);
               }

               @Override
               public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                  return Collections.unmodifiableMap(cache);
               }
            });
         }
      }
   }

   public static class Debuggable implements Debugable {

      @Override
      public Cache getCache(String cacheName) {
         return new Cache();
      }

      private static class Cache implements Debugable.Cache {

         @Override
         public void debugKey(Object key) {
            System.out.println(key);
         }

         @Override
         public void debugInfo() {
            System.out.println("Debug info");
         }
      }
   }

   public static class CacheListeners implements org.radargun.traits.CacheListeners {

      private List<CreatedListener> createdListeners = new ArrayList<>();
      private List<UpdatedListener> updatedListeners = new ArrayList<>();
      private List<RemovedListener> removedListeners = new ArrayList<>();
      private List<EvictedListener> evictedListeners = new ArrayList<>();
      private List<ExpiredListener> expiredListeners = new ArrayList<>();

      @Override
      public Collection<Type> getSupportedListeners() {
         return Arrays.asList(org.radargun.traits.CacheListeners.Type.values());
      }

      @Override
      public void addCreatedListener(String cacheName, CreatedListener listener, boolean sync) {
         createdListeners.add(listener);
      }

      @Override
      public void addUpdatedListener(String cacheName, UpdatedListener listener, boolean sync) {
         updatedListeners.add(listener);
      }

      @Override
      public void addRemovedListener(String cacheName, RemovedListener listener, boolean sync) {
         removedListeners.add(listener);
      }

      @Override
      public void addEvictedListener(String cacheName, EvictedListener listener, boolean sync) {
         evictedListeners.add(listener);
      }

      @Override
      public void addExpiredListener(String cacheName, ExpiredListener listener, boolean sync) {
         expiredListeners.add(listener);
      }

      @Override
      public void removeCreatedListener(String cacheName, CreatedListener listener, boolean sync) {
         createdListeners.remove(listener);
      }

      @Override
      public void removeUpdatedListener(String cacheName, UpdatedListener listener, boolean sync) {
         updatedListeners.remove(listener);
      }

      @Override
      public void removeRemovedListener(String cacheName, RemovedListener listener, boolean sync) {
         removedListeners.remove(listener);
      }

      @Override
      public void removeEvictedListener(String cacheName, EvictedListener listener, boolean sync) {
         evictedListeners.remove(listener);
      }

      @Override
      public void removeExpiredListener(String cacheName, ExpiredListener listener, boolean sync) {
         expiredListeners.remove(listener);
      }
   }
}
