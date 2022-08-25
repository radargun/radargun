package org.radargun.stages.cache.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

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
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TemporalOperations;

/**
 * @author Martin Gencur &lt;mgencur@redhat.com&gt;
 *
 * This stage allows for testing operations that have lifespan or maxIdle attributes set.
 *
 */
@Namespace(name = TestStage.NAMESPACE, deprecatedName = TestStage.DEPRECATED_NAMESPACE)
@Stage(doc = "Test using TemporalOperations")
public class TemporalOperationsTestStage extends CacheOperationsTestStage {

   @Property(doc = "Lifespan to be used for all temporal operations. Default is 1000 ms.")
   protected int lifespan = 1000;

   @Property(doc = "MaxIdle time to be used for all temporal operations. Default is -1 (no MaxIdle time set)")
   protected int maxIdle = -1;

   @Property(doc = "Ratio of GET requests. Default is 4.")
   protected int getRatio = 4;

   @Property(doc = "Ratio of PUT_WITH_LIFESPAN requests. Default is 1.")
   protected int putWithLifespanRatio = 1;

   @Property(doc = "Ratio of PUT_WITH_LIFESPAN_AND_MAXIDLE requests. Default is 0.")
   protected int putWithLifespanAndMaxIdleRatio = 0;

   @Property(doc = "Ratio of GET_AND_PUT_WITH_LIFESPAN requests. Default is 0.")
   protected int getAndPutWithLifespanRatio = 0;

   @Property(doc = "Ratio of GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE requests. Default is 0.")
   protected int getAndPutWithLifespanAndMaxIdleRatio = 0;

   @Property(doc = "Ratio of PUT_IF_ABSENT_WITH_LIFESPAN requests. Default is 0.")
   protected int putIfAbsentWithLifespanRatio = 0;

   @Property(doc = "Ratio of PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE requests. Default is 0.")
   protected int putIfAbsentWithLifespanAndMaxIdleRatio = 0;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicOperations basicOperations;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected TemporalOperations temporalOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      statisticsPrototype.registerOperationsGroup(TemporalOperations.class.getSimpleName() + ".Total",
         new HashSet<>(Arrays.asList(
            BasicOperations.GET,
            TemporalOperations.PUT_WITH_LIFESPAN,
            TemporalOperations.GET_AND_PUT_WITH_LIFESPAN,
            TemporalOperations.PUT_WITH_LIFESPAN_AND_MAXIDLE,
            TemporalOperations.GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE,
            TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN,
            TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE)));

      return new RatioOperationSelector.Builder()
         .add(BasicOperations.GET, getRatio)
         .add(TemporalOperations.PUT_WITH_LIFESPAN, putWithLifespanRatio)
         .add(TemporalOperations.GET_AND_PUT_WITH_LIFESPAN, getAndPutWithLifespanRatio)
         .add(TemporalOperations.PUT_WITH_LIFESPAN_AND_MAXIDLE, putWithLifespanAndMaxIdleRatio)
         .add(TemporalOperations.GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE, getAndPutWithLifespanAndMaxIdleRatio)
         .add(TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN, putIfAbsentWithLifespanRatio)
         .add(TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE, putIfAbsentWithLifespanAndMaxIdleRatio)
         .build();
   }

   @Override
   public OperationLogic createLogic() {
      return new Logic();
   }

   protected class Logic extends OperationLogic {
      protected TemporalOperations.Cache nonTxTemporalCache, temporalCache;
      protected BasicOperations.Cache nonTxBasicCache, basicCache;
      protected KeySelector keySelector;

      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         this.nonTxTemporalCache = temporalOperations.getCache(cacheName);
         this.nonTxBasicCache = basicOperations.getCache(cacheName);
         if (useTransactions(cacheName)) {
            temporalCache = new Delegates.TemporalOperationsCache();
            basicCache = new Delegates.BasicOperationsCache();
         } else {
            temporalCache = nonTxTemporalCache;
            basicCache = nonTxBasicCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
         keySelector = getKeySelector(stressor);
      }

      @Override
      public void transactionStarted() {
         ((Delegates.BasicOperationsCache) basicCache).setDelegate(stressor.wrap(nonTxBasicCache));
         ((Delegates.TemporalOperationsCache) temporalCache).setDelegate(stressor.wrap(nonTxTemporalCache));
      }

      @Override
      public void transactionEnded() {
         ((Delegates.BasicOperationsCache) basicCache).setDelegate(null);
         ((Delegates.TemporalOperationsCache) temporalCache).setDelegate(null);
      }

      @Override
      public void run(Operation operation) throws RequestException {
         Object key = keyGenerator.generateKey(keySelector.next());
         Random random = stressor.getRandom();

         Invocation invocation;
         if (operation == BasicOperations.GET) {
            invocation = new CacheInvocations.Get(basicCache, key);
         } else if (operation == TemporalOperations.PUT_WITH_LIFESPAN) {
            invocation = new CacheInvocations.PutWithLifespan(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan);
         } else if (operation == TemporalOperations.PUT_WITH_LIFESPAN_AND_MAXIDLE) {
            invocation = new CacheInvocations.PutWithLifespanAndMaxIdle(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan, maxIdle);
         } else if (operation == TemporalOperations.GET_AND_PUT_WITH_LIFESPAN) {
            invocation = new CacheInvocations.GetAndPutWithLifespan(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan);
         } else if (operation == TemporalOperations.GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE) {
            invocation = new CacheInvocations.GetAndPutWithLifespanAndMaxIdle(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan, maxIdle);
         } else if (operation == TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN) {
            invocation = new CacheInvocations.PutIfAbsentWithLifespan(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan);
         } else if (operation == TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE) {
            invocation = new CacheInvocations.PutIfAbsentWithLifespanAndMaxIdle(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan, maxIdle);
         } else throw new IllegalArgumentException(operation.name);
         stressor.makeRequest(invocation);
      }
   }
}
