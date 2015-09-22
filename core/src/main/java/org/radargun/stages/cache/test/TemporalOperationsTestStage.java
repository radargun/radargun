package org.radargun.stages.cache.test;

import org.radargun.Operation;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.Invocation;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.TemporalOperations;

import java.util.Random;

/**
 * @author Martin Gencur &lt;mgencur@redhat.com&gt;
 *
 * This stage allows for testing operations that have lifespan or maxIdle attributes set.
 *
 */
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

   protected OperationSelector operationSelector;

   @Init
   public void init() {
      operationSelector = new OperationSelector.Builder()
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
   public OperationLogic getLogic() {
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
         if (!useTransactions(cacheName)) {
            temporalCache = nonTxTemporalCache;
            basicCache = nonTxBasicCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
         keySelector = getKeySelector(stressor);
      }

      @Override
      public void transactionStarted() {
         basicCache = stressor.wrap(nonTxBasicCache);
         temporalCache = stressor.wrap(nonTxTemporalCache);
      }

      @Override
      public void transactionEnded() {
         basicCache = null;
         temporalCache = null;
      }

      @Override
      public Object run() throws RequestException {
         Object key = keyGenerator.generateKey(keySelector.next());
         Random random = stressor.getRandom();
         Operation operation = operationSelector.next(random);

         Invocation invocation;
         if (operation == BasicOperations.GET) {
            invocation = new Invocations.Get(basicCache, key);
            return stressor.makeRequest(invocation);
         } else if (operation == TemporalOperations.PUT_WITH_LIFESPAN) {
            invocation = new Invocations.PutWithLifespan(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan);
         } else if (operation == TemporalOperations.PUT_WITH_LIFESPAN_AND_MAXIDLE) {
            invocation = new Invocations.PutWithLifespanAndMaxIdle(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan, maxIdle);
         } else if (operation == TemporalOperations.GET_AND_PUT_WITH_LIFESPAN) {
            invocation = new Invocations.GetAndPutWithLifespan(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan);
         } else if (operation == TemporalOperations.GET_AND_PUT_WITH_LIFESPAN_AND_MAXIDLE) {
            invocation = new Invocations.GetAndPutWithLifespanAndMaxIdle(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan, maxIdle);
         } else if (operation == TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN) {
            invocation = new Invocations.PutIfAbsentWithLifespan(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan);
         } else if (operation == TemporalOperations.PUT_IF_ABSENT_WITH_LIFESPAN_AND_MAXIDLE) {
            invocation = new Invocations.PutIfAbsentWithLifespanAndMaxIdle(temporalCache, key, valueGenerator.generateValue(key, entrySize.next(random), random), lifespan, maxIdle);
         } else throw new IllegalArgumentException(operation.name);
         return stressor.makeRequest(invocation);
      }
   }
}
