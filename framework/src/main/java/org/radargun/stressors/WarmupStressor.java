package org.radargun.stressors;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.CacheWrapper;
import org.radargun.config.Property;
import org.radargun.config.Stressor;
import org.radargun.stages.WarmupStage;

/**
 * Do <code>operationCount</code> puts and  <code>operationCount</code> gets on the cache wrapper.
 *
 * @author Mircea.Markus@jboss.com
 * @deprecated this should be replaced with the {@link StressTestStressor}. This is because that warmup mimics better the
 * access pattern of the the {@link StressTestStressor}, especially in the case of transactions.
 */
@Stressor(doc = "Deprecated warmup stressor.")
public class WarmupStressor extends AbstractCacheWrapperStressor {

   private static Log log = LogFactory.getLog(WarmupStage.class);

   @Property(doc = "After how many operations should be log written. Default is 10000.")
   private int operationCount = 10000;

   @Property(doc = "Bucket into which the entries will be written. Default is WarmupStressor_BUCKET.")
   private String bucket = "WarmupStressor_BUCKET";

   @Property(doc = "Prefix for the keys. Default is WarmupStressor_KEY")
   private String keyPrefix = "WarmupStressor_KEY";

   private CacheWrapper wrapper;

   @Property(doc = "Number of threads used for the warmup. Default is 5.")
   private int numThreads = 5;

   @Property(doc = "Number of keys each thread will operate on. Default is 50.")
   private int keysPerThread = 50;

   public Map<String, Object> stress(CacheWrapper wrapper) {
      if (bucket == null || keyPrefix == null) {
         throw new IllegalStateException("Both bucket and key prefix must be set before starting to stress.");
      }
      if (wrapper == null) {
         throw new IllegalStateException("Null wrapper not allowed");
      }
      try {
         log.info("Performing Warmup Operations");
         performWarmupOperations(wrapper);
      } catch (Exception e) {
         log.warn("Received exception durring cache warmup" + e.getMessage());
      }
      return null;
   }

   public void performWarmupOperations(CacheWrapper w) throws Exception {
      this.wrapper = w;
      log.info("Cache launched, performing " + (Integer) operationCount + " put and get operations ");
      Thread[] warmupThreads = new Thread[numThreads];

      final AtomicInteger writes = new AtomicInteger(0);
      final AtomicInteger reads = new AtomicInteger(0);
      final Random r = new Random();

      for (int i = 0; i < numThreads; i++) {
         final int threadId = i;
         warmupThreads[i] = new Thread() {
            public void run() {
               while (writes.get() < operationCount && reads.get() < operationCount) {

                  boolean isGet = r.nextInt(2) == 1;
                  int operationId;
                  if (isGet) {
                     if ((operationId = reads.getAndIncrement()) < operationCount) doGet(operationId, threadId);
                  } else {
                     if ((operationId = writes.getAndIncrement()) < operationCount) doPut(operationId, threadId);
                  }
               }
            }
         };

         warmupThreads[i].start();
      }
      log.info("Joining warmupThreads");
      for (Thread t: warmupThreads) t.join();
      log.info("Cache warmup ended!");
   }

   private void doPut(int operationId, int threadId) {
      String key = new StringBuilder(keyPrefix).append("-").append(operationId % keysPerThread).append("-").
            append(threadId).append("-").append(bucket).toString();
      try {
         wrapper.put(bucket, key, key);
      } catch (Exception e) {
         log.info("Caught exception doing a PUT on key " + key, e);
      }
   }

   private void doGet(int operationId, int threadId) {
      String key = new StringBuilder(keyPrefix).append("-").append(operationId % keysPerThread).append("-").
            append(threadId).append("-").append(bucket).toString();
      try {
         wrapper.get(bucket, key);
      } catch (Exception e) {
         log.info("Caught exception doing a GET on key " + key, e);
      }
   }

   public void setOperationCount(int operationCount) {
      this.operationCount = operationCount;
   }

   public void setBucket(String bucket) {
      this.bucket = bucket;
   }

   public void setKeyPrefix(String keyPrefix) {
      this.keyPrefix = keyPrefix;
   }

   public void setNumThreads(int numThreads) {
      if (numThreads <=0) throw new IllegalStateException("Invalid num of threads:" + numThreads);
      this.numThreads = numThreads;
   }

   @Override
   public String toString() {
      return "WarmupStressor{" +
            "bucket=" + bucket +
            "keyPrefix" + keyPrefix +
            "operationCount=" + operationCount + "}";
   }


   public void destroy() throws Exception {
      wrapper = null;
   }
}
