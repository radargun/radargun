package org.radargun.stages.cache.test;

import java.math.BigDecimal;
import java.util.*;

import org.radargun.Operation;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.OperationSelector;
import org.radargun.stages.test.RatioOperationSelector;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.SizeConverter;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Namespace(name = TestStage.NAMESPACE, deprecatedName = TestStage.DEPRECATED_NAMESPACE)
@Stage(doc = "During execution, keys expire (entries are removed from the cache) and new keys are used.")
public class KeyExpirationTestStage extends CacheTestStage {

   @Property(doc = "Maximum number of entries stored in the cache by one stressor thread at one moment.")
   protected long numEntriesPerThread = 0;

   @Property(doc = "Maximum number of bytes in entries' values stored in the cache by one stressor thread at one moment.", converter = SizeConverter.class)
   protected long numBytesPerThread = 0;

   @Property(doc = "Due to configuration (eviction, expiration), some keys may spuriously disappear. Do not issue a warning for this situation. Default is false.")
   protected boolean expectLostKeys = false;

   @Property(doc = "With fixedKeys=false, maximum lifespan of an entry. Default is 1 hour.", converter = TimeConverter.class)
   protected long entryLifespan = 3600000;

   @Property(doc = "Ratio of GET requests. Default is 4.")
   protected int getRatio = 4;

   @Property(doc = "Ratio of PUT requests. Default is 1.")
   protected int putRatio = 1;

   @InjectTrait
   protected BasicOperations basicOperations;

   private OperationSelector operationSelector;

   @Override
   public void init() {
      super.init();
      operationSelector = new RatioOperationSelector.Builder()
         .add(BasicOperations.PUT, putRatio)
         .add(BasicOperations.GET, getRatio)
         .build();

      statisticsPrototype.registerOperationsGroup(BasicOperations.class.getSimpleName() + ".Total",
              new HashSet<>(Arrays.asList(
                      BasicOperations.GET,
                      CacheInvocations.Get.GET_NULL,
                      BasicOperations.PUT
              )));
      statisticsPrototype.registerOperationsGroup(BasicOperations.class.getSimpleName() + ".Total.TX",
              new HashSet<>(Arrays.asList(
                      CacheInvocations.Get.TX,
                      CacheInvocations.Put.TX
              )));
   }


   @Override
   public OperationLogic getLogic() {
      return new Logic();
   }

   /**
    * @author Radim Vansa &lt;rvansa@redhat.com&gt;
    */
   protected class Logic extends OperationLogic {
      private long minRemoveTimestamp = Long.MAX_VALUE;
      private int minRemoveSize = 0;
      private HashMap<Integer, Load> loadForSize = new HashMap<>();
      private int nextKeyIndex;
      private BasicOperations.Cache nonTxCache;
      private BasicOperations.Cache cache;

      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         nextKeyIndex = stressor.getGlobalThreadIndex();
         double averageSize = 0;
         Map<Integer, BigDecimal> probabilityMap = entrySize.getProbabilityMap();
         long entries;
         if (numBytesPerThread > 0) {
            for (Map.Entry<Integer, BigDecimal> entry : probabilityMap.entrySet()) {
               averageSize += entry.getValue().doubleValue() + entry.getKey();
            }
            entries = (long) (numBytesPerThread / averageSize);
         } else {
            entries = numEntriesPerThread;
         }
         long expectedMax = 0;
         for (Map.Entry<Integer, BigDecimal> entry : probabilityMap.entrySet()) {
            long valuesForSize = (long) (entries * entry.getValue().doubleValue());
            expectedMax += valuesForSize * entry.getKey();
            loadForSize.put(entry.getKey(), new Load(valuesForSize));
         }
         log.info("Expecting maximal load of " + new SizeConverter().convertToString(expectedMax));

         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         nonTxCache = basicOperations.getCache(cacheName);
         if (useTransactions(cacheName)) {
            cache = new Delegates.BasicOperationsCache<>();
         } else {
            cache = nonTxCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
      }

      @Override
      public void transactionStarted() {
         ((Delegates.BasicOperationsCache) cache).setDelegate(stressor.wrap(nonTxCache));
      }

      @Override
      public void transactionEnded() {
         ((Delegates.BasicOperationsCache) cache).setDelegate(null);
      }

      @Override
      public void run(Operation ignored) throws RequestException {
         Random random = stressor.getRandom();
         KeyWithRemovalTime pair;
         long timestamp = TimeService.currentTimeMillis();
         if (minRemoveTimestamp <= timestamp) {
            Load load = loadForSize.get(minRemoveSize);
            pair = load.scheduledKeys.pollFirst();
            Boolean value;
            try {
               value = (Boolean) stressor.makeRequest(new CacheInvocations.Remove(cache, pair.key));
            } catch (RequestException e) {
               load.scheduledKeys.add(pair);
               return;
            }
            updateMin();
            if (!value && !expectLostKeys) {
               log.error("REMOVE: Entry for key " + pair.key + " was not found!");
            }
         } else {
            Operation operation = operationSelector.next(random);
            if (operation == BasicOperations.GET && minRemoveTimestamp < Long.MAX_VALUE) {
               Load load = null;
               for (int attempt = 0; attempt < 100; ++attempt) {
                  load = loadForSize.get(entrySize.next(random));
                  if (!load.scheduledKeys.isEmpty()) break;
               }
               if (load.scheduledKeys.isEmpty()) {
                  log.error("Current load seems to be null but timestamp is " + minRemoveTimestamp);
                  return;
               }
               pair = getRandomPair(load.scheduledKeys, timestamp, random);
               Object value = stressor.makeRequest(new CacheInvocations.Get(cache, pair.key));
               if (value == null) {
                  if (expectLostKeys) {
                     load.scheduledKeys.remove(pair);
                     updateMin();
                  } else {
                     log.error("GET: Value for key " + pair.key + " is null!");
                  }
               }
            } else {
               int size = 0;
               Load load = null;
               for (int attempt = 0; attempt < 100; ++attempt) {
                  size = entrySize.next(random);
                  load = loadForSize.get(size);
                  if (load.max != 0) break;
               }
               if (load.max == 0) {
                  log.error("Cannot add any entry");
                  return;
               } else if (load.scheduledKeys.size() < load.max) {
                  long keyIndex = nextKeyIndex;
                  nextKeyIndex += getTotalThreads();
                  pair = new KeyWithRemovalTime(keyGenerator.generateKey(keyIndex), getRandomTimestamp(timestamp, random));
                  load.scheduledKeys.add(pair);
                  updateMin();
               } else {
                  pair = getRandomPair(load.scheduledKeys, timestamp, random);
               }
               Object value = valueGenerator.generateValue(null, size, stressor.getRandom());
               try {
                  stressor.makeRequest(new CacheInvocations.Put(cache, pair.key, value));
               } catch (RequestException e) {
                  load.scheduledKeys.remove(pair);
                  while (!isTerminated()) {
                     try {
                        stressor.makeRequest(new CacheInvocations.Remove(cache, pair.key));
                        return;
                     } catch (RequestException e1) {
                        // exception already logged in Stressor
                     }
                  }
               }
            }
         }
      }

      private void updateMin() {
         minRemoveTimestamp = Long.MAX_VALUE;
         for (Map.Entry<Integer, Load> entry : loadForSize.entrySet()) {
            if (!entry.getValue().scheduledKeys.isEmpty()) {
               long min = entry.getValue().scheduledKeys.first().removeTimestamp;
               if (min < minRemoveTimestamp) {
                  minRemoveTimestamp = min;
                  minRemoveSize = entry.getKey();
               }
            }
         }
      }

      private long getRandomTimestamp(long current, Random random) {
         // ~sqrt probability for 1 - maxRoot^2
         final long maxRoot = (long) Math.sqrt((double) entryLifespan);
         long rand = random.nextLong() % maxRoot;
         return current + rand * rand + random.nextLong() % (2 * maxRoot - 2) + 1;
      }

      private KeyWithRemovalTime getRandomPair(TreeSet<KeyWithRemovalTime> scheduledKeys, long timestamp, Random random) {
         // we cannot get random access to PriorityQueue and there is no SortedList or another appropriate structure
         KeyWithRemovalTime pair = scheduledKeys.floor(new KeyWithRemovalTime(null, getRandomTimestamp(timestamp, random)));
         return pair != null ? pair : scheduledKeys.first();
      }
   }

   private static class KeyWithRemovalTime implements Comparable<KeyWithRemovalTime> {
      public final Object key;
      public final long removeTimestamp;

      public KeyWithRemovalTime(Object key, long removeTimestamp) {
         this.key = key;
         this.removeTimestamp = removeTimestamp;
      }

      @Override
      public int compareTo(KeyWithRemovalTime o) {
         if (removeTimestamp < o.removeTimestamp) return -1;
         if (removeTimestamp > o.removeTimestamp) return 1;
         if (key == null || o.key == null) return 0;
         if (key.equals(o.key)) return 0;
         return -1;
      }
   }

   private static class Load {
      public final long max;
      public final TreeSet<KeyWithRemovalTime> scheduledKeys = new TreeSet<>();

      private Load(long max) {
         this.max = max;
      }
   }
}
