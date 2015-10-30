package org.radargun.stages.cache.test.legacy;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.legacy.LegacyStressor;
import org.radargun.utils.ReflexiveConverters;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Common ancestor for all xOperationsTestStages")
public abstract class CacheOperationsLegacyTestStage extends CacheLegacyTestStage {
   @Property(name = "keySelector", doc = "Selects which key IDs are used in the test.", optional = false,
         complexConverter = KeySelectorFactoryConverter.class)
   protected KeySelectorFactory keySelectorFactory;

   protected KeySelector getKeySelector(LegacyStressor stressor) {
      return keySelectorFactory.newInstance(CacheOperationsLegacyTestStage.this,
            stressor.getRandom(), stressor.getGlobalThreadIndex(), stressor.getThreadIndex());
   }

   private static class KeySelectorFactoryConverter extends ReflexiveConverters.ObjectConverter {
      protected KeySelectorFactoryConverter() {
         super(new Class<?>[] { CollidingKeysSelector.Factory.class, ConcurrentKeysSelector.Factory.class, GaussianKeysSelector.Factory.class});
      }
   }
}
