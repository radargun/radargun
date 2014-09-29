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
import org.radargun.state.ServiceListenerAdapter;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
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

   @InjectTrait
   protected BasicOperations basicOperations;

   protected List<Loader> loaders = new ArrayList<>();
   protected KeyGenerator keyGenerator;
   protected ValueGenerator valueGenerator;
   protected AtomicLong entryCounter = new AtomicLong(0);
   protected AtomicLong sizeSum = new AtomicLong(0);

   public DistStageAck executeOnSlave() {
      if (!isServiceRunning()) {
         log.info("Not running test on this slave as service is not running.");
         return successfulResponse();
      }
      log.info("Using key generator " + keyGeneratorClass + ", param " + keyGeneratorParam);
      keyGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), keyGeneratorClass, keyGeneratorParam);
      log.info("Using value generator " + valueGeneratorClass + ", param " + valueGeneratorParam);
      valueGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), valueGeneratorClass, valueGeneratorParam);

      slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      slaveState.put(CacheSelector.CACHE_SELECTOR, cacheSelector);
      slaveState.addServiceListener(new ServiceListenerAdapter() {
         @Override
         public void serviceDestroyed() {
            slaveState.remove(KeyGenerator.KEY_GENERATOR);
            slaveState.remove(ValueGenerator.VALUE_GENERATOR);
            slaveState.remove(CacheSelector.CACHE_SELECTOR);
         }
      });

      for (int i = 0; i < numThreads; ++i) {
         Loader loader = new Loader(i, getLoaderIds(i));
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
      long next(int size);
   }

   private static class RangeIds implements LoaderIds {
      private long current;
      private long limit;

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
   }

   private class Loader extends Thread {
      private int threadIndex;
      private Throwable throwable;
      private final Random random;
      private LoaderIds loaderIds;

      public Loader(int index, LoaderIds loaderIds) {
         super("Loader-" + index);
         this.loaderIds = loaderIds;
         threadIndex = slaveState.getSlaveIndex() * numThreads + index;
         random = seed == null ? new Random() : new Random(seed + threadIndex);
      }

      @Override
      public void run() {
         try {
            BasicOperations.Cache cache = basicOperations.getCache(cacheSelector.getCacheName(threadIndex));
            for (;;) {
               int size = entrySize.next(random);
               long keyId = loaderIds.next(size);
               if (keyId < 0) {
                  log.info("Finished loading entries");
                  break;
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
                  throwable = new Exception("Failed to insert entry key=" + key + ", value=" + value + " " + maxLoadAttempts + " times.");
                  return;
               }
               // just for logs - don't worry about those two not in sync
               long entryCount = entryCounter.incrementAndGet();
               long totalSize = sizeSum.addAndGet(size);
               if (entryCount % logPeriod == 0) {
                  log.infof("This node loaded %d entries (~%d bytes)", entryCount, totalSize);
               }
            }
         } catch (Throwable t) {
            log.error("Exception in Loader", t);
            throwable = t;
         }
      }

      public Exception getException() {
         return throwable == null ? null : new ExecutionException(throwable);
      }
   }
}
