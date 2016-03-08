package org.radargun.stages.cache.test.legacy;

import java.util.Random;

import org.radargun.Operation;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.cache.test.CacheInvocations;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.legacy.LegacyStressor;
import org.radargun.stages.test.legacy.LegacyTestStage;
import org.radargun.stages.test.legacy.OperationLogic;
import org.radargun.stages.test.legacy.OperationSelector;
import org.radargun.stages.test.legacy.RatioOperationSelector;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Namespace(LegacyTestStage.NAMESPACE)
@Stage(doc = "Test using BasicOperations")
public class BasicOperationsTestStage extends CacheOperationsTestStage {
   @Property(doc = "Ratio of GET requests. Default is 4.")
   protected int getRatio = 4;

   @Property(doc = "Ratio of CONTAINS requests. Default is 0.")
   protected int containsRatio = 0;

   @Property(doc = "Ratio of PUT requests. Default is 1.")
   protected int putRatio = 1;

   @Property(doc = "Ratio of GET_AND_PUT requests. Default is 0.")
   protected int getAndPutRatio = 0;

   @Property(doc = "Ratio of REMOVE requests. Default is 0.")
   protected int removeRatio = 0;

   @Property(doc = "Ratio of GET_AND_REMOVE requests. Default is 0.")
   protected int getAndRemoveRatio = 0;

   @InjectTrait
   protected BasicOperations basicOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      return new RatioOperationSelector.Builder()
         .add(BasicOperations.GET, getRatio)
         .add(BasicOperations.CONTAINS_KEY, containsRatio)
         .add(BasicOperations.PUT, putRatio)
         .add(BasicOperations.GET_AND_PUT, getAndPutRatio)
         .add(BasicOperations.REMOVE, removeRatio)
         .add(BasicOperations.GET_AND_REMOVE, getAndRemoveRatio)
         .build();
   }

   @Override
   public OperationLogic getLogic() {
      return new Logic();
   }

   protected class Logic extends OperationLogic {
      protected BasicOperations.Cache nonTxCache;
      protected BasicOperations.Cache cache;
      protected KeySelector keySelector;

      @Override
      public void init(LegacyStressor stressor) {
         super.init(stressor);
         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         this.nonTxCache = basicOperations.getCache(cacheName);
         if (useTransactions(cacheName)) {
            cache = new Delegates.BasicOperationsCache<>();
         } else {
            cache = nonTxCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
         keySelector = getKeySelector(stressor);
      }

      @Override
      public void transactionStarted() {
         ((Delegates.BasicOperationsCache) cache).setDelegate(stressor.wrap(nonTxCache));
      }

      @Override
      public void transactionEnded() {
         ((Delegates.BasicOperationsCache) cache).setDelegate(null);
      }

      @Override
      public void run(Operation operation) throws RequestException {
         Object key = keyGenerator.generateKey(keySelector.next());
         Random random = stressor.getRandom();

         Invocation invocation;
         if (operation == BasicOperations.GET) {
            invocation = new CacheInvocations.Get(cache, key);
         } else if (operation == BasicOperations.PUT) {
            invocation = new CacheInvocations.Put(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random));
         } else if (operation == BasicOperations.REMOVE) {
            invocation = new CacheInvocations.Remove(cache, key);
         } else if (operation == BasicOperations.CONTAINS_KEY) {
            invocation = new CacheInvocations.ContainsKey(cache, key);
         } else if (operation == BasicOperations.GET_AND_PUT) {
            invocation = new CacheInvocations.GetAndPut(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random));
         } else if (operation == BasicOperations.GET_AND_REMOVE) {
            invocation = new CacheInvocations.GetAndRemove(cache, key);
         } else throw new IllegalArgumentException(operation.name);
         stressor.makeRequest(invocation);
      }
   }
}
