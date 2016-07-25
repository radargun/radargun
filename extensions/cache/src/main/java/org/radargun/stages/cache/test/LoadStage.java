package org.radargun.stages.cache.test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;
import org.radargun.stages.test.TransactionMode;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.Fuzzy;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Loads data into the cache using specified generators.")
public class LoadStage extends org.radargun.stages.test.LoadStage {

   @Property(doc = "Total number of key-value entries that should be loaded into cache. Default is 100.")
   protected long numEntries = 100;

   @Property(doc = "Initial key ID used for numbering the keys. Default is 0.")
   protected long keyIdOffset = 0;

   @Property(doc = "Size of the value in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   protected Fuzzy<Integer> entrySize = Fuzzy.always(1000);

   @Property(doc = "Generator of keys (transforms key ID into key object). Default is 'string'.",
      complexConverter = KeyGenerator.ComplexConverter.class)
   protected KeyGenerator keyGenerator = new StringKeyGenerator();

   @Property(doc = "Generator of values. Default is byte-array.",
      complexConverter = ValueGenerator.ComplexConverter.class)
   protected ValueGenerator valueGenerator = new ByteArrayValueGenerator();

   @Property(doc = "Selects which caches will be loaded. Default is the default cache.",
      complexConverter = CacheSelector.ComplexConverter.class)
   protected CacheSelector cacheSelector = new CacheSelector.Default();

   @Property(doc = "This option forces local loading of all keys on all slaves in this group (not only numEntries/numNodes). Default is false.")
   protected boolean loadAllKeys = false;

   @Property(doc = "If set to true, the entries are removed instead of being inserted. Default is false.")
   private boolean remove = false;

   @Property(doc = "Specifies if the requests should be explicitly wrapped in transactions. " +
      "Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if " +
      "the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.")
   protected TransactionMode useTransactions = TransactionMode.IF_TRANSACTIONAL;

   @Property(doc = "Numbers of entries loaded in one transaction. Default is to not use transactions.")
   protected int transactionSize = 0;

   @Property(converter = TimeConverter.class, doc = "Target period of put operations - e.g. when this is set to 10 ms" +
      "the benchmark will try to do one put operation every 10 ms. By default the requests are executed at maximum speed.")
   protected long requestPeriod = 0;

   @Property(doc = "Size of batch to be loaded into cache (using putAll). If <= 0, put() operation is used sequentially.")
   protected int batchSize = 0;

   @Property(doc = "Controls whether batch insertion is performed in asychronous way. Default is false (prefer synchronous operations).")
   protected boolean useAsyncBatchLoading = false;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicOperations basicOperations;

   @InjectTrait(dependency = InjectTrait.Dependency.OPTIONAL)
   protected BulkOperations bulkOperations;

   @InjectTrait
   protected Transactional transactional;

   @Override
   protected void prepare() {
      if (useTransactions == TransactionMode.ALWAYS) {
         if (transactional == null) {
            throw new IllegalStateException("Service does not support transactions");
         } else if (transactionSize <= 0) {
            throw new IllegalStateException("Transaction size was not configured");
         }
      }

      slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      slaveState.put(CacheSelector.CACHE_SELECTOR, cacheSelector);
   }

   protected Loader createLoader(int threadBase, int threadIndex) {
      String cacheName = cacheSelector.getCacheName(threadBase + threadIndex);
      boolean useTransactions = this.useTransactions.use(transactional, cacheName, transactionSize);
      int totalThreads = (loadAllKeys ? 1 : getExecutingSlaves().size()) * numThreads;
      int globalThreadIndex = loadAllKeys ? threadIndex : threadBase + threadIndex;
      LoaderIds loaderIds = new RangeIds(keyIdOffset + numEntries * globalThreadIndex / totalThreads, keyIdOffset + numEntries * (globalThreadIndex + 1) / totalThreads);
      if (batchSize > 0) {
         if (batchSize > 0 && bulkOperations == null) {
            throw new IllegalArgumentException("Bulk operations have been enabled, but they are not supported by current service");
         }
         return useTransactions ? new BulkTxLoader(threadIndex, loaderIds) : new BulkNonTxLoader(threadIndex, loaderIds);
      } else {
         return useTransactions ? new TxLoader(threadIndex, loaderIds) : new NonTxLoader(threadIndex, loaderIds);
      }
   }

   private interface LoaderIds {
      /** Return next key ID */
      long next();

      /** Create a mark we can later return to */
      void mark();

      /** Resets current index to mark */
      void resetToMark();

      /* Returns index of the key */
      long currentKeyIndex();
   }

   private static class RangeIds implements LoaderIds {
      private final long start;
      private long current;
      private long limit;
      private long mark;

      public RangeIds(long from, long to) {
         start = current = from;
         limit = to;
      }

      @Override
      public long next() {
         if (current < limit) {
            return current++;
         } else {
            return -1;
         }
      }

      @Override
      public void mark() {
         mark = current;
      }

      @Override
      public void resetToMark() {
         current = mark;
      }

      @Override
      public long currentKeyIndex() {
         return current - start;
      }
   }

   private abstract class CacheLoader extends Loader {
      protected final LoaderIds loaderIds;
      protected long start;

      public CacheLoader(int index, LoaderIds loaderIds) {
         super(index);
         this.loaderIds = loaderIds;
         this.start = TimeService.nanoTime();
      }
   }

   private abstract class CacheTxLoader extends CacheLoader {

      protected Transactional.Transaction tx;
      protected int txCurrentSize;
      protected int txAttempts;
      protected long txValuesSize;
      protected long txBeginSeed;

      public CacheTxLoader(int index, LoaderIds loaderIds) {
         super(index, loaderIds);
      }

      protected void beginTx() {
         if (tx == null) {
            tx = transactional.getTransaction();
            wrapCache();
            txBeginSeed = Utils.getRandomSeed(random);
            try {
               tx.begin();
            } catch (Exception e) {
               log.error("Begin failed");
               throw e;
            }
            loaderIds.mark();
         }
      }

      /**
       * @return true when there are more keys to commit
       */
      protected boolean commitTx(long keyId) {
         if (txCurrentSize >= transactionSize || keyId < 0) {
            try {
               tx.commit();
               long entryCountToLog = batchSize > 0 ? txCurrentSize * batchSize : txCurrentSize;
               logLoaded(entryCountToLog, txValuesSize, remove);
               txAttempts = 0;
               txCurrentSize = 0;
               txValuesSize = 0;
               tx = null;
               resetCache();
            } catch (Exception e) {
               log.error("Failed to commit transaction", e);
               restartTx();
               return true;
            }
            if (keyId < 0) {
               log.info(String.format("Finished %s entries", remove ? "removing" : "loading"));
               return false;
            }
         }
         return true;
      }

      protected void restartTx() {
         loaderIds.resetToMark();
         Utils.setRandomSeed(random, txBeginSeed);
         txCurrentSize = 0;
         txValuesSize = 0;
         resetCache();
         tx = null;
         txAttempts++;
         if (txAttempts >= maxLoadAttempts) {
            throw new RuntimeException("Failed to commit transaction " + maxLoadAttempts + " times");
         }
      }

      protected abstract void wrapCache();

      protected abstract void resetCache();
   }

   private class NonTxLoader extends CacheLoader {
      private final BasicOperations.Cache<Object, Object> cache;

      public NonTxLoader(int index, LoaderIds loaderIds) {
         super(index, loaderIds);
         String cacheName = cacheSelector.getCacheName(threadIndex);
         cache = basicOperations.getCache(cacheName);
      }

      @Override
      protected boolean loadDataUnit() {
         int size = entrySize.next(random);
         long currentKeyIndex = loaderIds.currentKeyIndex();
         delayRequest(start, TimeService.nanoTime(), currentKeyIndex);
         long keyId = loaderIds.next();
         if (keyId < 0) {
            log.info(String.format("Finished %s entries", remove ? "removing" : "loading"));
            return false;
         }
         Object key = keyGenerator.generateKey(keyId);
         Object value = valueGenerator.generateValue(key, size, random);
         boolean success = false;
         for (int i = 0; i < maxLoadAttempts; ++i) {
            try {
               if (remove) {
                  cache.remove(key);
               } else {
                  cache.put(key, value);
               }
               success = true;
               break;
            } catch (Exception e) {
               log.warnf(e, "Attempt %d/%d to %s cache failed, waiting %d ms before next attempt",
                  i + 1, maxLoadAttempts, remove ? "remove entry from" : "insert entry into", waitOnError);
               try {
                  Thread.sleep(waitOnError);
               } catch (InterruptedException e1) {
                  log.warn("Interrupted when waiting after failed operation", e1);
               }
            }
         }
         if (!success) {
            String message = String.format("Failed to %s entry key=%s, value=%s %d times.",
               remove ? "remove" : "insert", key, value, maxLoadAttempts);
            throw new RuntimeException(message);
         }
         logLoaded(1, size, remove);
         return true;
      }
   }

   private class BulkNonTxLoader extends CacheLoader {
      private final BulkOperations.Cache<Object, Object> cache;

      public BulkNonTxLoader(int index, LoaderIds loaderIds) {
         super(index, loaderIds);
         String cacheName = cacheSelector.getCacheName(threadIndex);
         cache = bulkOperations.getCache(cacheName, useAsyncBatchLoading);
      }

      @Override
      protected boolean loadDataUnit() {
         Map<Object, Object> entryMap = new HashMap<>(batchSize);
         long keyId = 0;
         int totalSize = 0;
         for (int i = 0; i < batchSize; i++) {
            int size = entrySize.next(random);
            totalSize += size;
            long currentKeyIndex = loaderIds.currentKeyIndex();
            delayRequest(start, TimeService.nanoTime(), currentKeyIndex);
            keyId = loaderIds.next();
            if (keyId < 0) {
               if (entryMap.isEmpty()) {
                  log.info(String.format("Finished %s entries", remove ? "removing" : "loading"));
                  return false;
               }
               break;
            }
            Object key = keyGenerator.generateKey(keyId);
            Object value = valueGenerator.generateValue(key, size, random);
            entryMap.put(key, value);
         }
         for (int i = 0; i < maxLoadAttempts; i++) {
            try {
               if (remove) {
                  cache.removeAll(entryMap.keySet());
               } else {
                  cache.putAll(entryMap);
               }
               if (keyId < 0) {
                  log.info(String.format("Finished %s entries", remove ? "removing" : "loading"));
                  return false;
               }
               return true;
            } catch (Exception e) {
               log.warnf(e, "Attempt %d/%d to %s cache failed, waiting %d ms before next attempt",
                  i + 1, maxLoadAttempts, remove ? "remove entry from" : "insert entry into", waitOnError);
               try {
                  Thread.sleep(waitOnError);
               } catch (InterruptedException e1) {
                  log.warn("Interrupted when waiting after failed operation", e1);
               }
            }
         }
         // Reached only when maxLoadAttempts attempts have been attained
         String message = String.format("Failed to %s batch entries %d times.",
            remove ? "remove" : "insert", maxLoadAttempts);
         logLoaded(batchSize, totalSize, remove);
         throw new RuntimeException(message);
      }
   }

   private class TxLoader extends CacheTxLoader {
      private final BasicOperations.Cache<Object, Object> nonTxCache;
      private BasicOperations.Cache cache;

      public TxLoader(int index, LoaderIds loaderIds) {
         super(index, loaderIds);
         String cacheName = cacheSelector.getCacheName(threadIndex);
         nonTxCache = basicOperations.getCache(cacheName);
      }

      @Override
      protected boolean loadDataUnit() {
         beginTx();
         int size = entrySize.next(random);
         long currentKeyIndex = loaderIds.currentKeyIndex();
         delayRequest(start, TimeService.nanoTime(), currentKeyIndex);
         long keyId = loaderIds.next();
         if (keyId >= 0) {
            Object key = keyGenerator.generateKey(keyId);
            Object value = valueGenerator.generateValue(key, size, random);
            try {
               if (remove) {
                  cache.remove(key);
               } else {
                  cache.put(key, value);
               }
               txCurrentSize++;
               txValuesSize += size;
            } catch (Exception e) {
               log.warnf(e, "Attempt %d/%d to %s cache failed, waiting %d ms before next attempt",
                  txAttempts + 1, maxLoadAttempts, remove ? "remove entry from" : "insert entry into", waitOnError);
               try {
                  tx.rollback();
               } catch (Exception re) {
                  log.error("Failed to rollback transaction", re);
               }
               restartTx();
               try {
                  Thread.sleep(waitOnError);
               } catch (InterruptedException e1) {
                  log.warn("Interrupted when waiting after failed operation", e1);
               }
               return true;
            }
         } else {
            if (keyId < 0 && txCurrentSize <= 0) {
               log.info(String.format("Finished %s entries", remove ? "removing" : "loading"));
               return false;
            }
         }
         return commitTx(keyId);
      }

      @Override
      protected void wrapCache() {
         cache = tx.wrap(nonTxCache);
      }

      @Override
      protected void resetCache() {
         cache = null;
      }
   }

   private class BulkTxLoader extends CacheTxLoader {
      private final BulkOperations.Cache<Object, Object> nonTxCache;
      private BulkOperations.Cache cache;

      public BulkTxLoader(int index, LoaderIds loaderIds) {
         super(index, loaderIds);
         String cacheName = cacheSelector.getCacheName(threadIndex);
         nonTxCache = bulkOperations.getCache(cacheName, useAsyncBatchLoading);
      }

      @Override
      protected boolean loadDataUnit() {
         beginTx();
         Map<Object, Object> entryMap = new HashMap<>(batchSize);
         long keyId = 0;
         long totalSize = 0;
         for (int i = 0; i < batchSize; i++) {
            int size = entrySize.next(random);
            totalSize += size;
            long currentKeyIndex = loaderIds.currentKeyIndex();
            delayRequest(start, TimeService.nanoTime(), currentKeyIndex);
            keyId = loaderIds.next();
            if (keyId >= 0) {
               Object key = keyGenerator.generateKey(keyId);
               Object value = valueGenerator.generateValue(key, size, random);
               entryMap.put(key, value);
            } else {
               if (entryMap.isEmpty()) {
                  log.info(String.format("Finished %s entries", remove ? "removing" : "loading"));
                  // Ongoing uncommitted TX?
                  return txCurrentSize > 0 ? commitTx(keyId) : false;
               }
               break;
            }
         }
         try {
            if (remove) {
               cache.removeAll(entryMap.keySet());
            } else {
               cache.putAll(entryMap);
            }
            txCurrentSize++;
            txValuesSize += totalSize;
         } catch (Exception e) {
            log.warnf(e, "Attempt %d/%d to %s cache failed, waiting %d ms before next attempt",
               txAttempts + 1, maxLoadAttempts, remove ? "remove entry from" : "insert entry into", waitOnError);
            try {
               tx.rollback();
            } catch (Exception re) {
               log.error("Failed to rollback transaction", re);
            }
            restartTx();
            try {
               Thread.sleep(waitOnError);
            } catch (InterruptedException e1) {
               log.warn("Interrupted when waiting after failed operation", e1);
            }
            return true;
         }
         return commitTx(keyId);
      }

      @Override
      protected void wrapCache() {
         cache = tx.wrap(nonTxCache);
      }

      @Override
      protected void resetCache() {
         cache = null;
      }
   }

   private void delayRequest(long start, long currentTime, long keyIndex) {
      if (requestPeriod > 0) {
         long nanoDelay = TimeUnit.MILLISECONDS.toNanos(requestPeriod);
         long timeToWait = TimeUnit.NANOSECONDS.toMillis(start + keyIndex * nanoDelay - currentTime);
         if (timeToWait > 0) {
            Utils.sleep(timeToWait);
         }
      }
   }
}
