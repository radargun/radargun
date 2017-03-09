package org.radargun.stages.cache.test;

import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestStage;
import org.radargun.utils.ReflexiveConverters;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Namespace(name = TestStage.NAMESPACE, deprecatedName = TestStage.DEPRECATED_NAMESPACE)
@Stage(doc = "Common ancestor for all xOperationsTestStages")
public abstract class CacheOperationsTestStage extends CacheTestStage {
   @Property(name = "keySelector", doc = "Selects which key IDs are used in the test.", optional = false,
      complexConverter = KeySelectorFactoryConverter.class)
   protected KeySelectorFactory keySelectorFactory;

   protected KeySelector getKeySelector(Stressor stressor) {
      return keySelectorFactory.newInstance(CacheOperationsTestStage.this,
         stressor.getRandom(), stressor.getGlobalThreadIndex(), stressor.getThreadIndex());
   }

   private static class KeySelectorFactoryConverter extends ReflexiveConverters.ObjectConverter {
      protected KeySelectorFactoryConverter() {
         super(new Class<?>[] {CollidingKeysSelector.Factory.class, ConcurrentKeysSelector.Factory.class, GaussianKeysSelector.Factory.class});
      }
   }
}
