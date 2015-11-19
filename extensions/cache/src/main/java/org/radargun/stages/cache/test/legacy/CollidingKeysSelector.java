package org.radargun.stages.cache.test.legacy;

import java.util.Random;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.stages.test.legacy.LegacyTestStage;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CollidingKeysSelector implements KeySelector {
   private final Random random;
   private final long numEntries;

   public CollidingKeysSelector(Random random, long numEntries) {
      this.random = random;
      this.numEntries = numEntries;
   }

   @Override
   public long next() {
      return (random.nextLong() & Long.MAX_VALUE) % numEntries;
   }

   @Namespace(LegacyTestStage.NAMESPACE)
   @DefinitionElement(name = "colliding-keys", doc = "In the test fixed set of entries is used and this is shared among all stressor threads.")
   public static class Factory implements KeySelectorFactory {
      @Property(doc = "Total number of key-value entries.", optional = false)
      private long numEntries = 0;

      @Override
      public KeySelector newInstance(CacheOperationsTestStage stage, Random random, int globalThreadId, int threadId) {
         return new CollidingKeysSelector(random, numEntries);
      }

      @Override
      public String toString() {
         return CollidingKeysSelector.class.getSimpleName() + PropertyHelper.toString(this);
      }
   }
}
