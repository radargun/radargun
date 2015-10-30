package org.radargun.stages.cache.test;

import java.util.Random;

import org.radargun.Operation;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.config.Stage;
import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.stages.cache.generators.ValueGenerator;
import org.radargun.stages.test.Conversation;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestSetupStage;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.Fuzzy;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Benchmark where several client threads access cache limited by time or number of requests.")
public abstract class CacheTestSetupStage extends TestSetupStage {

   @Property(doc = "Size of the value in bytes. Default is 1000.", converter = Fuzzy.IntegerConverter.class)
   protected Fuzzy<Integer> entrySize = Fuzzy.always(1000);

   @Property(doc = "Generator of keys used in the test (transforms key ID into key object). By default the generator is retrieved from slave state.",
         complexConverter = KeyGenerator.ComplexConverter.class)
   protected KeyGenerator keyGenerator = null;

   @Property(doc = "Generator of values used in the test. By default the generator is retrieved from slave state.",
         complexConverter = ValueGenerator.ComplexConverter.class)
   protected ValueGenerator valueGenerator = null;

   @Property(doc = "Name of the cache. Default is the default cache.")
   protected String cacheName;

   @Property(doc = "Total number of key-value entries.", optional = false)
   protected long numEntries;

   @InjectTrait
   protected ConditionalOperations conditionalOperations;

   @Override
   protected void prepare() {
      super.prepare();
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
   }

   protected Object getRandomKey(Random random) {
      return keyGenerator.generateKey((random.nextLong() & Long.MAX_VALUE) % numEntries);
   }

   protected static abstract class BaseTxConversation<CacheType> implements Conversation {
      private final Operation txOperation;
      private final TxInvocationSetting invocationSetting;

      public BaseTxConversation(Operation txOperation, TxInvocationSetting invocationSetting) {
         this.txOperation = txOperation;
         this.invocationSetting = invocationSetting;
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         Transactional.Transaction tx = getTransaction();
         try {
            CacheType txCache = tx.wrap(getCache());
            stressor.startTransaction(tx);

            Random random = stressor.getRandom();
            for (int i = 0; i < invocationSetting.transactionSize; ++i) {
               invoke(stressor, txCache, random);
            }

            if (invocationSetting.shouldCommit(random)) {
               stressor.commitTransaction(tx, txOperation);
            } else {
               stressor.rollbackTransaction(tx, txOperation);
            }
            tx = null;
         } finally {
            if (tx != null) stressor.rollbackTransaction(tx, txOperation);
         }
      }

      protected abstract CacheType getCache();

      protected abstract Transactional.Transaction getTransaction();

      protected abstract void invoke(Stressor stressor, CacheType cache, Random random);
   }
}
