package org.radargun.stages.cache.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.radargun.Operation;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
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

   protected OperationSelector operationSelector;

   @Init
   public void init() {
      operationSelector = new OperationSelector.Builder()
            .add(BasicOperations.GET, getRatio)
            .add(BasicOperations.CONTAINS_KEY, containsRatio)
            .add(BasicOperations.PUT, putRatio)
            .add(BasicOperations.GET_AND_PUT, getAndPutRatio)
            .add(BasicOperations.REMOVE, removeRatio)
            .add(BasicOperations.GET_AND_REMOVE, getAndRemoveRatio)
            .build();
      statisticsPrototype.registerOperationsGroup(BasicOperations.class.getSimpleName() + ".Total",
                                                  new HashSet<>(Arrays.asList(
                                                        BasicOperations.GET,
                                                        Invocations.Get.GET_NULL,
                                                        BasicOperations.CONTAINS_KEY,
                                                        BasicOperations.PUT,
                                                        BasicOperations.GET_AND_PUT,
                                                        BasicOperations.REMOVE,
                                                        BasicOperations.GET_AND_REMOVE)));
      statisticsPrototype.registerOperationsGroup(BasicOperations.class.getSimpleName() + ".Total.TX",
                                                  new HashSet<>(Arrays.asList(
                                                        Invocations.Get.GET_TX,
                                                        Invocations.Get.GET_NULL_TX,
                                                        Invocations.ContainsKey.TX,
                                                        Invocations.Put.TX,
                                                        Invocations.GetAndPut.TX,
                                                        Invocations.Remove.TX,
                                                        Invocations.GetAndRemove.TX
                                                  )));
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
      public void init(Stressor stressor) {
         super.init(stressor);
         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         this.nonTxCache = basicOperations.getCache(cacheName);
         if (!useTransactions(cacheName)) {
            cache = nonTxCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
         keySelector = getKeySelector(stressor);
      }

      @Override
      public void transactionStarted() {
         cache = stressor.wrap(nonTxCache);
      }

      @Override
      public void transactionEnded() {
         cache = null;
      }

      @Override
      public Object run() throws RequestException {
         Object key = keyGenerator.generateKey(keySelector.next());
         Random random = stressor.getRandom();
         Operation operation = operationSelector.next(random);

         Invocation invocation;
         if (operation == BasicOperations.GET) {
            invocation = new Invocations.Get(cache, key);
            return stressor.makeRequest(invocation);
         } else if (operation == BasicOperations.PUT) {
            invocation = new Invocations.Put(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random));
         } else if (operation == BasicOperations.REMOVE) {
            invocation = new Invocations.Remove(cache, key);
         } else if (operation == BasicOperations.CONTAINS_KEY) {
            invocation = new Invocations.ContainsKey(cache, key);
         } else if (operation == BasicOperations.GET_AND_PUT) {
            invocation = new Invocations.GetAndPut(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random));
         } else if (operation == BasicOperations.GET_AND_REMOVE) {
            invocation = new Invocations.GetAndRemove(cache, key);
         } else throw new IllegalArgumentException(operation.name);
         return stressor.makeRequest(invocation);
      }
   }
}
