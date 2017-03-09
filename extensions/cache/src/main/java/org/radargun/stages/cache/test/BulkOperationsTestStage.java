package org.radargun.stages.cache.test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.OperationSelector;
import org.radargun.stages.test.RatioOperationSelector;
import org.radargun.stages.test.Stressor;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.InjectTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Namespace(name = TestStage.NAMESPACE, deprecatedName = TestStage.DEPRECATED_NAMESPACE)
@Stage(doc = "Executes operations from BulkOperations trait.")
public class BulkOperationsTestStage extends CacheOperationsTestStage {
   @Property(doc = "Number of keys inserted/retrieved within one operation. Applicable only when the cache wrapper" +
      " supports bulk operations. Default is 10.")
   protected int bulkSize = 10;

   @Property(doc = "Ratio of GET_ALL_NATIVE requests. Default is 4.")
   protected int getAllNativeRatio = 4;

   @Property(doc = "Ratio of GET_ALL_ASYNC requests. Default is 0.")
   protected int getAllAsyncRatio = 0;

   @Property(doc = "Ratio of PUT_ALL_NATIVE requests. Default is 1.")
   protected int putAllNativeRatio = 1;

   @Property(doc = "Ratio of PUT_ALL_ASYNC requests. Default is 0.")
   protected int putAllAsyncRatio = 0;

   @Property(doc = "Ratio of REMOVE_ALL_NATIVE requests. Default is 0.")
   protected int removeAllNativeRatio = 0;

   @Property(doc = "Ratio of REMOVE_ALL_ASYNC requests. Default is 0.")
   protected int removeAllAsyncRatio = 0;

   @InjectTrait
   protected BulkOperations bulkOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      return new RatioOperationSelector.Builder()
         .add(BulkOperations.GET_ALL_NATIVE, getAllNativeRatio)
         .add(BulkOperations.GET_ALL_ASYNC, getAllAsyncRatio)
         .add(BulkOperations.PUT_ALL_NATIVE, putAllNativeRatio)
         .add(BulkOperations.PUT_ALL_ASYNC, putAllAsyncRatio)
         .add(BulkOperations.REMOVE_ALL_NATIVE, removeAllNativeRatio)
         .add(BulkOperations.REMOVE_ALL_ASYNC, removeAllAsyncRatio)
         .build();
   }

   @Override
   public OperationLogic getLogic() {
      return new Logic();
   }

   protected class Logic extends OperationLogic {
      private BulkOperations.Cache nonTxNativeCache;
      private BulkOperations.Cache nativeCache;
      private BulkOperations.Cache nonTxAsyncCache;
      private BulkOperations.Cache asyncCache;
      private KeySelector keySelector;

      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         this.nonTxNativeCache = bulkOperations.getCache(cacheName, false);
         this.nonTxAsyncCache = bulkOperations.getCache(cacheName, true);
         if (useTransactions(cacheName)) {
            nativeCache = new Delegates.BulkOperationsCache();
            asyncCache = new Delegates.BulkOperationsCache();
         } else {
            nativeCache = nonTxNativeCache;
            asyncCache = nonTxAsyncCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
         keySelector = getKeySelector(stressor);
      }

      @Override
      public void transactionStarted() {
         ((Delegates.BulkOperationsCache) nativeCache).setDelegate(stressor.wrap(nonTxNativeCache));
         ((Delegates.BulkOperationsCache) asyncCache).setDelegate(stressor.wrap(nonTxAsyncCache));
      }

      @Override
      public void transactionEnded() {
         ((Delegates.BulkOperationsCache) nativeCache).setDelegate(null);
         ((Delegates.BulkOperationsCache) asyncCache).setDelegate(null);
      }

      @Override
      public void run(Operation operation) throws RequestException {
         Random random = stressor.getRandom();

         Invocation invocation;
         if (operation == BulkOperations.PUT_ALL_NATIVE || operation == BulkOperations.PUT_ALL_ASYNC) {
            Map<Object, Object> map = new HashMap<>(bulkSize);
            for (int i = 0; i < bulkSize; ) {
               Object key = keyGenerator.generateKey(keySelector.next());
               if (!map.containsKey(key)) {
                  map.put(key, valueGenerator.generateValue(key, entrySize.next(random), random));
                  ++i;
               }
            }
            if (operation == BulkOperations.PUT_ALL_NATIVE) {
               invocation = new CacheInvocations.PutAll(nativeCache, false, map);
            } else {
               invocation = new CacheInvocations.PutAll(asyncCache, true, map);
            }
         } else {
            Set<Object> set = new HashSet<>(bulkSize);
            for (int i = 0; i < bulkSize; ) {
               Object key = keyGenerator.generateKey(keySelector.next());
               if (!set.contains(key)) {
                  set.add(key);
                  ++i;
               }
            }
            if (operation == BulkOperations.GET_ALL_NATIVE || operation == BulkOperations.GET_ALL_ASYNC) {
               if (operation == BulkOperations.GET_ALL_NATIVE) {
                  invocation = new CacheInvocations.GetAll(nativeCache, false, set);
               } else {
                  invocation = new CacheInvocations.GetAll(asyncCache, true, set);
               }
            } else {
               if (operation == BulkOperations.REMOVE_ALL_NATIVE) {
                  invocation = new CacheInvocations.RemoveAll(nativeCache, false, set);
               } else {
                  invocation = new CacheInvocations.RemoveAll(asyncCache, true, set);
               }
            }
         }
         stressor.makeRequest(invocation);
      }
   }
}
