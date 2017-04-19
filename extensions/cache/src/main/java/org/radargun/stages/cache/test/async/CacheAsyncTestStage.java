package org.radargun.stages.cache.test.async;

import java.util.concurrent.ThreadLocalRandom;

import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;
import org.radargun.stages.test.async.AsyncTestStage;
import org.radargun.utils.Fuzzy;

@Stage(doc = "Common base for cache operations")
public abstract class CacheAsyncTestStage extends AsyncTestStage {
   @Property(doc = "Size of the value in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   protected Fuzzy<Integer> entrySize = Fuzzy.uniform(1000);

   @Property(doc = "Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.",
         complexConverter = KeyGenerator.ComplexConverter.class)
   protected KeyGenerator keyGenerator = null;

   @Property(doc = "Generator of values used in the test. By default the generator is retrieved from slave state.",
         complexConverter = ValueGenerator.ComplexConverter.class)
   protected ValueGenerator valueGenerator = null;

   @Property(doc = "Selects which caches will be used in the test. By default the selector is retrieved from slave state.",
         complexConverter = CacheSelector.ComplexConverter.class)
   protected CacheSelector cacheSelector = null;

   @Property(doc = "Number of keys used, accessed with uniform probability.", optional = false)
   protected long numEntries;

   @Override
   protected void prepare() {
      if (keyGenerator == null) {
         keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
         if (keyGenerator == null) {
            throw new IllegalStateException("Key generator was not specified and no key generator was used before.");
         }
      } else {
         slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      }
      log.info("Using key generator " + keyGenerator.getClass().getName() + PropertyHelper.toString(keyGenerator));

      if (valueGenerator == null) {
         valueGenerator = (ValueGenerator) slaveState.get(ValueGenerator.VALUE_GENERATOR);
         if (valueGenerator == null) {
            throw new IllegalStateException("Value generator was not specified and no key generator was used before.");
         }
      } else {
         slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      }
      log.info("Using value generator " + valueGenerator.getClass().getName() + PropertyHelper.toString(valueGenerator));

      if (cacheSelector == null) {
         cacheSelector = (CacheSelector) slaveState.get(CacheSelector.CACHE_SELECTOR);
         if (cacheSelector == null) {
            throw new IllegalStateException("No cache selector defined.");
         }
      } else {
         slaveState.put(CacheSelector.CACHE_SELECTOR, cacheSelector);
      }
      log.info("Using cache selector " + cacheSelector);
   }

   protected Object getRandomKey(ThreadLocalRandom random) {
      return keyGenerator.generateKey(random.nextLong(numEntries));
   }

   protected Object getRandomValue(ThreadLocalRandom random, Object key) {
      return valueGenerator.generateValue(key, entrySize.next(random), random);
   }
}
