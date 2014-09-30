package org.radargun.stages.cache.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;
import org.radargun.stages.test.TransactionMode;
import org.radargun.state.ServiceListenerAdapter;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.Fuzzy;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Loads data into the cache using specified generators.")
public class LoadDataStage extends AbstractDistStage {
   @Property(doc = "Number of loaded entries after which a log entry should be written. Default is 10000.")
   protected long logPeriod = 10000;

   @Property(doc = "Total number of key-value entries that should be loaded into cache. Default is 100.")
   protected long numEntries = 100;

   @Property(doc = "Size of the value in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   protected Fuzzy<Integer> entrySize = Fuzzy.always(1000);

   @Property(doc = "The number of threads that should load the entries on one slave. Default is 10.")
   protected int numThreads = 10;

   @Property(doc = "Full class name of the key generator. Default is org.radargun.stressors.StringKeyGenerator.")
   protected String keyGeneratorClass = StringKeyGenerator.class.getName();

   @Property(doc = "Used to initialize the key generator. Null by default.")
   protected String keyGeneratorParam = null;

   @Property(doc = "Full class name of the value generator. Default is org.radargun.stressors.ByteArrayValueGenerator if useConditionalOperations=false and org.radargun.stressors.WrappedArrayValueGenerator otherwise.")
   protected String valueGeneratorClass = ByteArrayValueGenerator.class.getName();

   @Property(doc = "Used to initialize the value generator. Null by default.")
   protected String valueGeneratorParam = null;

   @Property(doc = "Which buckets will the stressors use. Available is 'none' (no buckets = null)," +
         "'thread' (each thread will use bucked_/threadId/) or " +
         "'all:/bucketName/' (all threads will use bucketName). Default is 'none'.",
         converter = CacheSelector.Converter.class)
   protected CacheSelector cacheSelector = new CacheSelector(CacheSelector.Type.DEFAULT, null);

   @Property(doc = "This option forces local loading of all keys on all slaves in this group (not only numEntries/numNodes). Default is false.")
   protected boolean loadAllKeys = false;

   @Property(doc = "Seed used for initialization of random generators - with same seed (and other arguments)," +
         " the stage guarantees same entries added to the cache. By default the seed is not set.")
   protected Long seed;

   @Property(doc = "During loading phase, if the insert fails, try it again. This is the maximum number of attempts. Default is 10.")
   protected int maxLoadAttempts = 10;

   @Property(doc = "Specifies if the requests should be explicitly wrapped in transactions. " +
         "Options are NEVER, ALWAYS and IF_TRANSACTIONAL: transactions are used only if " +
         "the cache configuration is transactional and transactionSize > 0. Default is IF_TRANSACTIONAL.")
   protected TransactionMode useTransactions = TransactionMode.IF_TRANSACTIONAL;

   @Property(doc = "Numbers of entries loaded in one transaction. Default is to not use transactions.")
   protected int transactionSize = 0;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicOperations basicOperations;

   @InjectTrait
   protected Transactional transactional;

   protected KeyGenerator keyGenerator;
   protected ValueGenerator valueGenerator;
   protected AtomicLong entryCounter = new AtomicLong(0);
   protected AtomicLong sizeSum = new AtomicLong(0);

   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }
      if (useTransactions == TransactionMode.ALWAYS) {
         if (transactional == null) {
            return errorResponse("Service does not support transactions");
         } else if (transactionSize <= 0) {
            return errorResponse("Transaction size was not configured");
         }
      }
      log.info("Using key generator " + keyGeneratorClass + ", param " + keyGeneratorParam);
      keyGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), keyGeneratorClass, keyGeneratorParam);
      log.info("Using value generator " + valueGeneratorClass + ", param " + valueGeneratorParam);
      valueGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), valueGeneratorClass, valueGeneratorParam);

      slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      slaveState.put(CacheSelector.CACHE_SELECTOR, cacheSelector);
      slaveState.addServiceListener(new Cleanup(slaveState));

      int threadBase = getExecutingSlaveIndex() * numThreads;
      List<Loader> loaders = new ArrayList<>();
      for (int i = 0; i < numThreads; ++i) {
         String cacheName = cacheSelector.getCacheName(threadBase + i);
         boolean useTransactions = this.useTransactions.use(transactional, cacheName, transactionSize);
         Loader loader = useTransactions ? new TxLoader(i, getLoaderIds(i)) : new NonTxLoader(i, getLoaderIds(i));
         loaders.add(loader);
         loader.start(); // no special synchronization needed
      }
      for (Loader loader : loaders) {
         try {
            loader.join();
         } catch (InterruptedException e) {
            return errorResponse("Interrupted when waiting for the loader to finish");
         }
         if (loader.getException() != null) {
            return errorResponse("Loader failed with exception", loader.getException());
         }
      }
      return successfulResponse();
   }

   private LoaderIds getLoaderIds(int index) {
      int totalThreads = (loadAllKeys ? 1 : getExecutingSlaves().size()) * numThreads;
      int threadIndex = loadAllKeys ? index : getExecutingSlaveIndex() * numThreads + index;
      return new RangeIds(numEntries * threadIndex / totalThreads, numEntries * (threadIndex + 1) / totalThreads);
   }

   private interface LoaderIds {
      /** Return next key ID for key with given size*/
      long next(int size);
      /** Create a mark we can later return to */
      void mark();
      void resetToMark();
   }

   private static class RangeIds implements LoaderIds {
      private long current;
      private long limit;
      private long mark;

      public RangeIds(long from, long to) {
         current = from;
         limit = to;
      }

      @Override
      public long next(int size) {
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
   }

   private abstract class Loader extends Thread {
      protected final Random random;
      protected final int threadIndex;
      protected final LoaderIds loaderIds;
      protected Throwable throwable;

      private Loader(int index, LoaderIds loaderIds) {
         super("Loader-" + index);
         this.loaderIds = loaderIds;
         threadIndex = slaveState.getSlaveIndex() * numThreads + index;
         random = seed == null ? new Random() : new Random(seed + threadIndex);
      }

      public Exception getException() {
         return throwable == null ? null : new ExecutionException(throwable);
      }

      @Override
      public void run() {
         try {
            for (; ; ) {
               if (!loadEntry()) return;
            }
         } catch (Throwable t) {
            log.error("Exception in Loader", t);
            throwable = t;
         }
      }

      protected abstract boolean loadEntry();
   }

   private class NonTxLoader extends Loader {
      private final BasicOperations.Cache<Object, Object> cache;

      public NonTxLoader(int index, LoaderIds loaderIds) {
         super(index, loaderIds);
         String cacheName = cacheSelector.getCacheName(threadIndex);
         cache = basicOperations.getCache(cacheName);
      }

      @Override
      protected boolean loadEntry() {
         int size = entrySize.next(random);
         long keyId = loaderIds.next(size);
         if (keyId < 0) {
            log.info("Finished loading entries");
            return false;
         }
         Object key = keyGenerator.generateKey(keyId);
         Object value = valueGenerator.generateValue(key, size, random);
         boolean inserted = false;
         for (int i = 0; i < maxLoadAttempts; ++i) {
            try {
               cache.put(key, value);
               inserted = true;
               break;
            } catch (Exception e) {
               log.warn("Failed to insert entry into cache", e);
            }
         }
         if (!inserted) {
            throw new RuntimeException("Failed to insert entry key=" + key + ", value=" + value + " " + maxLoadAttempts + " times.");
         }
         logLoaded(1, size);
         return true;
      }
   }

   private class TxLoader extends Loader {
      private final BasicOperations.Cache<Object, Object> nonTxCache;
      private Transactional.Transaction tx;
      private BasicOperations.Cache cache;
      private int txCurrentSize;
      private int txAttempts;
      private long txValuesSize;
      private long txBeginSeed;

      public TxLoader(int index, LoaderIds loaderIds) {
         super(index, loaderIds);
         String cacheName = cacheSelector.getCacheName(threadIndex);
         nonTxCache = basicOperations.getCache(cacheName);
      }

      @Override
      protected boolean loadEntry() {
         if (tx == null) {
            tx = transactional.getTransaction();
            cache = tx.wrap(nonTxCache);
            txBeginSeed = Utils.getRandomSeed(random);
            try {
               tx.begin();
            } catch (Exception e) {
               log.error("Begin failed");
               throw e;
            }
            loaderIds.mark();
         }
         int size = entrySize.next(random);
         long keyId = loaderIds.next(size);
         if (keyId >= 0) {
            Object key = keyGenerator.generateKey(keyId);
            Object value = valueGenerator.generateValue(key, size, random);
            try {
               cache.put(key, value);
               txCurrentSize++;
               txValuesSize += size;
            } catch (Exception e) {
               log.warn("Failed to insert entry into cache", e);
               try {
                  tx.rollback();
               } catch (Exception re) {
                  log.error("Failed to rollback transaction", re);
               }
               restartTx();
               return true;
            }
         }
         if (txCurrentSize >= transactionSize || keyId < 0) {
            try {
               tx.commit();
               logLoaded(txCurrentSize, txValuesSize);
               txAttempts = 0;
               txCurrentSize = 0;
               txValuesSize = 0;
               tx = null;
               cache = null;
            } catch (Exception e) {
               log.error("Failed to commit transaction", e);
               restartTx();
               return true;
            }
            if (keyId < 0) {
               log.info("Finished loading entries");
               return false;
            }
         }
         return true;
      }

      private void restartTx() {
         loaderIds.resetToMark();
         Utils.setRandomSeed(random, txBeginSeed);
         txCurrentSize = 0;
         txValuesSize = 0;
         cache = null;
         tx = null;
         txAttempts++;
         if (txAttempts >= maxLoadAttempts) {
            throw new RuntimeException("Failed to commit transaction " + maxLoadAttempts + " times");
         }
      }
   }

   private void logLoaded(long entries, long size) {
      long prevEntryCount, currentEntryCount;
      do {
         prevEntryCount = entryCounter.get();
         currentEntryCount = prevEntryCount + entries;
      } while (!entryCounter.compareAndSet(prevEntryCount, currentEntryCount));
      // just for logs - don't worry about those two not in sync
      long totalSize = sizeSum.addAndGet(size);
      if (prevEntryCount / logPeriod < currentEntryCount / logPeriod) {
         log.infof("This node loaded %d entries (~%d bytes)", currentEntryCount, totalSize);
      }
   }

   private static class Cleanup extends ServiceListenerAdapter {
      private final SlaveState slaveState;

      public Cleanup(SlaveState slaveState) {
         this.slaveState = slaveState;
      }

      @Override
      public void serviceDestroyed() {
         slaveState.remove(KeyGenerator.KEY_GENERATOR);
         slaveState.remove(ValueGenerator.VALUE_GENERATOR);
         slaveState.remove(CacheSelector.CACHE_SELECTOR);
         slaveState.removeServiceListener(this);
      }
   }
}
