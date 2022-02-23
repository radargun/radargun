package org.radargun.stages.cache.test;

import java.util.Random;
import java.util.UUID;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Namespace;
import org.radargun.stages.test.TestStage;

public class UUIDKeysSelector implements KeySelector {

   public UUIDKeysSelector() {
   }

   @Override
   public long next() {
      return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
   }

   @Namespace(name = TestStage.NAMESPACE, deprecatedName = TestStage.DEPRECATED_NAMESPACE)
   @DefinitionElement(name = "uuid-keys", doc = "Each thread will try to generate a unique key. There is no guarantee that a unique key will be generated.")
   public static class Factory implements KeySelectorFactory {

      @Override
      public KeySelector newInstance(CacheOperationsTestStage stage, Random random, int globalThreadId, int threadId) {
         return new UUIDKeysSelector();
      }
   }
}
