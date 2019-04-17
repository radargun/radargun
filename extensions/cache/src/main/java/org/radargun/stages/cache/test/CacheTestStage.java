package org.radargun.stages.cache.test;

import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.Fuzzy;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Namespace(name = TestStage.NAMESPACE, deprecatedName = TestStage.DEPRECATED_NAMESPACE)
@Stage(doc = "Benchmark where several client threads access cache limited by time or number of requests.")
public abstract class CacheTestStage extends TestStage {

   @Property(doc = "Size of the value in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   protected Fuzzy<Integer> entrySize = Fuzzy.uniform(1000);

   @Property(doc = "Generator of keys used in the test (transforms key ID into key object). Default is StringKeyGenerator.",
      complexConverter = KeyGenerator.ComplexConverter.class)
   protected KeyGenerator keyGenerator = StageFactory.createDefaultKeyGenerator();

   @Property(doc = "Generator of values used in the test. Default is ByteArrayValueGenerator.",
      complexConverter = ValueGenerator.ComplexConverter.class)
   protected ValueGenerator valueGenerator = StageFactory.createDefaultValueGenerator();

   @Property(doc = "Selects which caches will be used in the test. Default is CacheSelector.Default",
      complexConverter = CacheSelector.ComplexConverter.class)
   protected CacheSelector cacheSelector = StageFactory.createDefaultCacheSelector();

   @InjectTrait
   protected ConditionalOperations conditionalOperations;

   @Override
   protected void prepare() {
      slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      slaveState.put(CacheSelector.CACHE_SELECTOR, cacheSelector);

      log.info("Using key generator " + keyGenerator.getClass().getName() + PropertyHelper.toString(keyGenerator));
      log.info("Using value generator " + valueGenerator.getClass().getName() + PropertyHelper.toString(valueGenerator));
      log.info("Using cache selector " + cacheSelector);
   }
}
