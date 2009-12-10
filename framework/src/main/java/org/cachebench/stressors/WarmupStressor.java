package org.cachebench.stressors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.CacheWrapperStressor;
import org.cachebench.stages.WarmupStage;

import java.util.Map;

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

   public Map<String, String> stress(CacheWrapper wrapper) {
      if (bucket == null || keyPrefix == null) {
         throw new IllegalStateException("Both bucket and key prefix must be set before starting to stress.");
      }
      if (wrapper == null) {
         throw new IllegalStateException("Null wrapper not allowed");
      }
      try {
         performWarmupOperations(wrapper);
      } catch (Exception e) {
         log.warn("Received exception durring cache warmup" + e.getMessage());
      }
      return null;
   }

   public void performWarmupOperations(CacheWrapper wrapper) throws Exception {
      this.wrapper = wrapper;
      log.info("Cache launched, performing " + (Integer) operationCount + " put and get operations ");
      for (int i = 0; i < operationCount; i++) {
         try {
            wrapper.put(bucket, keyPrefix + String.valueOf((Integer) i), String.valueOf(i));
         }
         catch (Throwable e) {
            log.warn("Exception on cache warmup", e);
         }
      }

      for (int i = 0; i < operationCount; i++) {
         wrapper.get(bucket, keyPrefix + String.valueOf((Integer) i));
      }
      log.trace("Cache warmup ended!");
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
