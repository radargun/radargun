package org.radargun.stages.cache.test;

import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.helpers.CacheSelector;
import org.radargun.stages.test.TestStage;
import org.radargun.state.ServiceListenerAdapter;
import org.radargun.state.SlaveState;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.utils.Fuzzy;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Benchmark where several client threads access cache limited by time or number of requests.")
public abstract class CacheTestStage extends TestStage {

   @Property(doc = "Size of the value in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   protected Fuzzy<Integer> entrySize = Fuzzy.always(1000);

   @Property(doc = "Full class name of the key generator. By default the generator is retrieved from slave state.")
   protected String keyGeneratorClass = null;

   @Property(doc = "Used to initialize the key generator. Null by default.")
   protected String keyGeneratorParam = null;

   @Property(doc = "Full class name of the value generator. By default the generator is retrieved from slave state.")
   protected String valueGeneratorClass = null;

   @Property(doc = "Used to initialize the value generator. Null by default.")
   protected String valueGeneratorParam = null;

   @Property(doc = "Which buckets will the stressors use. Available is 'none' (no buckets = null)," +
         "'thread' (each thread will use bucked_/threadId/) or " +
         "'all:/bucketName/' (all threads will use bucketName). Default is the one retrieved from slave state.",
         converter = CacheSelector.Converter.class)
   protected CacheSelector cacheSelector = null;

   @InjectTrait
   protected ConditionalOperations conditionalOperations;

   protected KeyGenerator keyGenerator;
   protected ValueGenerator valueGenerator;

   @Override
   public void initOnSlave(final SlaveState slaveState) {
      super.initOnSlave(slaveState);
      if (!shouldExecute()) return;

      if (keyGeneratorClass == null) {
         keyGenerator = (KeyGenerator) slaveState.get(KeyGenerator.KEY_GENERATOR);
         if (keyGenerator == null) {
            throw new IllegalStateException("Key generator was not specified and no key generator was used before.");
         }
      } else {
         keyGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), keyGeneratorClass, keyGeneratorParam);
         slaveState.put(KeyGenerator.KEY_GENERATOR, keyGenerator);
      }
      log.info("Using key generator " + keyGenerator.getClass().getName() + PropertyHelper.toString(keyGenerator));

      if (valueGeneratorClass == null) {
         valueGenerator = (ValueGenerator) slaveState.get(ValueGenerator.VALUE_GENERATOR);
         if (valueGenerator == null) {
            throw new IllegalStateException("Value generator was not specified and no key generator was used before.");
         }
      } else {
         valueGenerator = Utils.instantiateAndInit(slaveState.getClassLoader(), valueGeneratorClass, valueGeneratorParam);
         slaveState.put(ValueGenerator.VALUE_GENERATOR, valueGenerator);
      }
      log.info("Using value generator " + valueGenerator.getClass().getName() + PropertyHelper.toString(valueGenerator));

      if (cacheSelector == null) {
         if ((cacheSelector = (CacheSelector) slaveState.get(CacheSelector.CACHE_SELECTOR)) == null){
            throw new IllegalStateException("No cache selector defined.");
         }
      } else {
         slaveState.put(CacheSelector.CACHE_SELECTOR, cacheSelector);
      }
      log.info("Using cache selector " + cacheSelector);

      slaveState.addServiceListener(new ServiceListenerAdapter() {
         @Override
         public void serviceDestroyed() {
            slaveState.remove(KeyGenerator.KEY_GENERATOR);
            slaveState.remove(ValueGenerator.VALUE_GENERATOR);
            slaveState.remove(CacheSelector.CACHE_SELECTOR);
         }
      });
   }
}
