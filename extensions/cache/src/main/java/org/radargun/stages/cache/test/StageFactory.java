package org.radargun.stages.cache.test;

import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;

/**
 * When benchmarking a scalability tests the @{@link CacheTestStage} won't have the key, value or selector because the
 * slave will be initialized later and won't be part of the @{@link LoadStage}. The current implementation was using
 * the @{@link LoadStage} to put the key, value or selector into the slaveState
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class StageFactory {

   private StageFactory() {

   }

   public static KeyGenerator createDefaultKeyGenerator() {
      return new StringKeyGenerator();
   }

   public static ValueGenerator createDefaultValueGenerator() {
      return new ByteArrayValueGenerator();
   }

   public static CacheSelector createDefaultCacheSelector() {
      return new CacheSelector.Default();
   }

}
