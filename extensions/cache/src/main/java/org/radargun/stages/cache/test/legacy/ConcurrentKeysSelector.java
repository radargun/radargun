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
public class ConcurrentKeysSelector implements KeySelector {
   private final long offset;
   private final long size;
   private final Random random;

   public ConcurrentKeysSelector(Random random, long offset, long size) {
      this.random = random;
      this.offset = offset;
      this.size = size;
   }

   @Override
   public long next() {
      return offset + (random.nextLong() & Long.MAX_VALUE) % size;
   }

   @Namespace(LegacyTestStage.NAMESPACE)
   @DefinitionElement(name = "concurrent-keys", doc = "Each thread works with its own private set of keys, fixed for the whole duration of the test.")
   public static class Factory implements KeySelectorFactory {
      @Property(doc = "Number of key-value entries per each thread. You have to set either this or total-entries.")
      protected long numEntriesPerThread = 0;

      @Property(doc = "Total number of key-value entries. You have to set either this or num-entries-per-thread.")
      protected long totalEntries = 0;

      public void init() {
         if (numEntriesPerThread > 0 && totalEntries > 0) {
            throw new IllegalStateException("Only one of num-entries-per-thread, total-entries can be set.");
         } else if (numEntriesPerThread <= 0 || totalEntries <= 0) {
            throw new IllegalStateException("Either num-entries-per-thread or total-entries have to be set.");
         }
      }

      @Override
      public KeySelector newInstance(CacheOperationsTestStage stage, Random random, int globalThreadId, int threadId) {
         int totalThreads = stage.getTotalThreads();
         if (numEntriesPerThread > 0) {
            return new ConcurrentKeysSelector(random, numEntriesPerThread * globalThreadId, numEntriesPerThread);
         } else if (totalEntries > 0) {
            if (totalEntries < totalThreads) {
               throw new IllegalStateException("Number of total threads cannot be greater than number of total entries.");
            }
            long offset = totalEntries * globalThreadId / totalThreads;
            long end = totalEntries * (globalThreadId + 1) / totalThreads;
            return new ConcurrentKeysSelector(random, offset, end - offset);
         } else {
            throw new IllegalStateException("Either num-entries-per-thread or total-entries have to be set.");
         }
      }

      @Override
      public String toString() {
         return ConcurrentKeysSelector.class.getSimpleName() + PropertyHelper.toString(this);
      }
   }
}
