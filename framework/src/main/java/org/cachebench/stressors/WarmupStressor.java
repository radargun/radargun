package org.cachebench.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.CacheWrapperStressor;
import org.cachebench.stages.WarmupStage;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Do <code>operationCount</code> puts and  <code>operationCount</code> gets on the cache wrapper.
 *
 * @author Mircea.Markus@jboss.com
 */
public class WarmupStressor implements CacheWrapperStressor {

   private static Log log = LogFactory.getLog(WarmupStage.class);

   private int operationCount = 10000;

   private String bucket = "WarmupStressor_BUCKET";

   private String keyPrefix = "WarmupStressor_KEY";

   private CacheWrapper wrapper;

   private static final int WARMUP_THREADS = 5; // yes, hard coded.

   public Map<String, String> stress(CacheWrapper wrapper) {
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
      Thread[] warmupThreads = new Thread[WARMUP_THREADS];

      final AtomicInteger writes = new AtomicInteger(0);
      final AtomicInteger reads = new AtomicInteger(0);
      final Random r = new Random();

      for (int i = 0; i < WARMUP_THREADS; i++) {
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
      String key = new StringBuilder(keyPrefix).append("-").append(operationId).append("-").
            append(threadId).append("-").append(bucket).toString();
      try {
         wrapper.put(bucket, key, key);
      } catch (Exception e) {
         log.info("Caught exception doing a PUT on key " + key, e);
      }
   }

   private void doGet(int operationId, int threadId) {
      String key = new StringBuilder(keyPrefix).append("-").append(operationId).append("-").
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

   @Override
   public String toString() {
      return "WarmupStressor{" +
            "bucket=" + bucket +
            "keyPrefix" + keyPrefix +
            "operationCount=" + operationCount + "}";
   }


   public void destroy() throws Exception {
      wrapper.empty();
      wrapper = null;
   }
}
