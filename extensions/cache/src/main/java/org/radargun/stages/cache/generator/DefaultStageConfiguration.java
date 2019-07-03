package org.radargun.stages.cache.generator;

import org.radargun.stages.cache.generators.ByteArrayValueGenerator;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.StringKeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;

/**
 * When the benchmark file doesn't contains the  {@link org.radargun.stages.cache.test.LoadStage} it is returning a NPE
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class DefaultStageConfiguration {

   private DefaultStageConfiguration() {

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
