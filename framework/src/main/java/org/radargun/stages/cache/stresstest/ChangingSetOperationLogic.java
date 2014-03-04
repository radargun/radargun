package org.radargun.stages.cache.stresstest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.config.SizeConverter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.Operation;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
class ChangingSetOperationLogic implements OperationLogic {
   private static Log log = LogFactory.getLog(StressTestStage.class);

   private long minRemoveTimestamp = Long.MAX_VALUE;
   private int minRemoveSize = 0;
   private HashMap<Integer, Load> loadForSize = new HashMap<Integer, Load>();
   private AtomicLong keysLoaded = new AtomicLong(0);
   private StressTestStage stage;
   private int numNodes;

   public ChangingSetOperationLogic(StressTestStage stage) {
      this.stage = stage;
   }

   @Override
   public void init(int threadIndex, int nodeIndex, int numNodes) {
      this.numNodes = numNodes;
      keysLoaded.compareAndSet(0, nodeIndex);
      double averageSize = 0;
      Map<Integer, Double> probabilityMap = stage.entrySize.getProbabilityMap();
      long entries;
      if (stage.numBytes > 0) {
         for (Map.Entry<Integer, Double> entry : probabilityMap.entrySet()) {
            averageSize += entry.getValue() * entry.getKey();
         }
         entries = (long) (stage.numBytes / averageSize);
      } else {
         entries = stage.numEntries;
      }
      long expectedMax = 0;
      for (Map.Entry<Integer, Double> entry : probabilityMap.entrySet()) {
         long valuesForSize = (long) (entries * entry.getValue());
         expectedMax += valuesForSize * entry.getKey();
         loadForSize.put(entry.getKey(), new Load(valuesForSize));
      }
      log.info("Expecting maximal load of " + new SizeConverter().convertToString(expectedMax));
   }

   @Override
   public Object run(Stressor stressor) throws RequestException {
      Random r = ThreadLocalRandom.current();
      KeyWithRemovalTime pair;
      long timestamp = System.currentTimeMillis();
      if (minRemoveTimestamp <= timestamp) {
         Load load = loadForSize.get(minRemoveSize);
         pair = load.scheduledKeys.pollFirst();
         Object value;
         try {
            value = stressor.makeRequest(Operation.REMOVE, pair.key);
         } catch (RequestException e) {
            load.scheduledKeys.add(pair);
            return null;
         }
         updateMin();
         if (value == null && !stage.expectLostKeys) {
            log.error("REMOVE: Value for key " + pair.key + " is null!");
         }
         return value;
      } else if (r.nextInt(100) >= stage.writePercentage && minRemoveTimestamp < Long.MAX_VALUE) {
         // we cannot get random access to PriorityQueue and there is no SortedList or another appropriate structure
         Load load = null;
         for (int attempt = 0; attempt < 100; ++attempt) {
            load = loadForSize.get(stage.entrySize.next(r));
            if (!load.scheduledKeys.isEmpty()) break;
         }
         if (load.scheduledKeys.isEmpty()) {
            log.error("Current load seems to be null but timestamp is " + minRemoveTimestamp);
            return null;
         }
         pair = getRandomPair(load.scheduledKeys, timestamp, r);
         Object value = stressor.makeRequest(Operation.GET, pair.key);
         if (value == null) {
            if (stage.expectLostKeys) {
               load.scheduledKeys.remove(pair);
               updateMin();
            } else {
               log.error("GET: Value for key " + pair.key + " is null!");
            }
         }
         return value;
      } else {
         Object value = stage.generateValue(null, Integer.MAX_VALUE);
         int size = stage.getValueGenerator().sizeOf(value);
         Load load = loadForSize.get(size);
         if (load.scheduledKeys.size() < load.max) {
            long keyIndex = keysLoaded.getAndAdd(numNodes);
            pair = new KeyWithRemovalTime(stage.getKeyGenerator().generateKey(keyIndex), getRandomTimestamp(timestamp, r));
            load.scheduledKeys.add(pair);
            updateMin();
         } else {
            pair = getRandomPair(load.scheduledKeys, timestamp, r);
         }
         try {
            return stressor.makeRequest(Operation.PUT, pair.key, value);
         } catch (RequestException e) {
            load.scheduledKeys.remove(pair);
            for (;;) {
               try {
                  return stressor.makeRequest(Operation.REMOVE, pair.key);
               } catch (RequestException e1) {
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
      final long maxRoot = (long) Math.sqrt((double) stage.entryLifespan);
      long rand = random.nextLong() % maxRoot;
      return current + rand * rand + random.nextLong() % (2*maxRoot - 2) + 1;
   }

   private KeyWithRemovalTime getRandomPair(TreeSet<KeyWithRemovalTime> scheduledKeys, long timestamp, Random random) {
      KeyWithRemovalTime pair = scheduledKeys.floor(new KeyWithRemovalTime(null, getRandomTimestamp(timestamp, random)));
      return pair != null ? pair : scheduledKeys.first();
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
      public long max;
      public TreeSet<KeyWithRemovalTime> scheduledKeys = new TreeSet<KeyWithRemovalTime>();

      private Load(long max) {
         this.max = max;
      }
   }
}
