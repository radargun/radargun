package org.radargun.stages.cache.test.legacy;

import java.util.Random;

import org.radargun.Operation;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.cache.test.CacheInvocations;
import org.radargun.stages.test.legacy.LegacyStressor;
import org.radargun.stages.test.legacy.LegacyTestStage;
import org.radargun.stages.test.legacy.OperationLogic;
import org.radargun.stages.test.legacy.OperationSelector;
import org.radargun.stages.test.legacy.RatioOperationSelector;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.InjectTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Namespace(LegacyTestStage.NAMESPACE)
@Stage(doc = "Tests (atomic) conditional operations. Note that there is no put-if-absent-ratio" +
      "- this operation is executed anytime the selected key does not have value.")
public class ConditionalOperationsTestStage extends CacheOperationsTestStage {

   @Property(doc = "Ratio of REMOVE requests. Default is 1.")
   protected int removeRatio = 1;

   @Property(doc = "Ratio of REPLACE requests. Default is 1.")
   protected int replaceRatio = 1;

   @Property(doc = "Ratio of REPLACE_ANY requests. Default is 1.")
   protected int replaceAnyRatio = 1;

   @Property(doc = "Ratio of GET_AND_REPLACE requests. Default is 1.")
   protected int getAndReplaceRatio = 1;

   @Property(doc = "Percentage of requests in which the condition should be satisfied. Default is 50%.")
   protected int matchPercentage = 50;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicOperations basicOperations;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected ConditionalOperations conditionalOperations;

   @Override
   protected OperationSelector createOperationSelector() {
      return new RatioOperationSelector.Builder()
            .add(ConditionalOperations.REMOVE, removeRatio)
            .add(ConditionalOperations.REPLACE, replaceRatio)
            .add(ConditionalOperations.REPLACE_ANY, replaceAnyRatio)
            .add(ConditionalOperations.GET_AND_REPLACE, getAndReplaceRatio)
            .build();
   }

   @Override
   public OperationLogic getLogic() {
      return new Logic();
   }

   protected class MatchSelector {
      long matching = 0;
      long failing = 0;

      public void record(boolean matching) {
         if (matching) this.matching++;
         else this.failing++;
      }

      public boolean shouldMatch() {
         return matching * 100 / (matching + failing) <= matchPercentage;
      }
   }

   protected class Logic extends OperationLogic {
      protected MatchSelector matchSelector = new MatchSelector();
      protected BasicOperations.Cache nonTxBasicCache, basicCache;
      protected ConditionalOperations.Cache nonTxConditionalCache, conditionalCache;
      protected KeySelector keySelector;

      @Override
      public void init(LegacyStressor stressor) {
         super.init(stressor);
         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         nonTxBasicCache = basicOperations.getCache(cacheName);
         nonTxConditionalCache = conditionalOperations.getCache(cacheName);
         if (useTransactions(cacheName)) {
            basicCache = new Delegates.BasicOperationsCache();
            conditionalCache = new Delegates.ConditionalOperationsCache();
         } else {
            basicCache = nonTxBasicCache;
            conditionalCache = nonTxConditionalCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
         keySelector = getKeySelector(stressor);
      }

      @Override
      public void transactionStarted() {
         ((Delegates.BasicOperationsCache) basicCache).setDelegate(stressor.wrap(nonTxBasicCache));
         ((Delegates.ConditionalOperationsCache) conditionalCache).setDelegate(stressor.wrap(nonTxConditionalCache));
      }

      @Override
      public void transactionEnded() {
         ((Delegates.BasicOperationsCache) basicCache).setDelegate(null);
         ((Delegates.ConditionalOperationsCache) conditionalCache).setDelegate(null);
      }

      @Override
      public void run(Operation operation) throws RequestException {
         Random random = stressor.getRandom();
         Object key = keyGenerator.generateKey(keySelector.next());
         Object newValue = valueGenerator.generateValue(key, entrySize.next(random), random);
         boolean shouldMatch = matchSelector.shouldMatch();

         Object oldValue = stressor.makeRequest(new CacheInvocations.Get(basicCache, key));

         if (oldValue == null) {
            Object prevValue;
            if (shouldMatch) {
               prevValue = stressor.makeRequest(new CacheInvocations.PutIfAbsent(conditionalCache, key, newValue));
               matchSelector.record(prevValue == null);
            } else {
               prevValue = stressor.makeRequest(new CacheInvocations.GetAndReplace(conditionalCache, key, newValue));
               matchSelector.record(prevValue != null);
            }
         } else if (operation == ConditionalOperations.REMOVE) {
            Boolean removed = (Boolean) stressor.makeRequest(new CacheInvocations.RemoveConditionally(conditionalCache, key,
                  shouldMatch ? oldValue : newValue));
            matchSelector.record(removed);
         } else if (operation == ConditionalOperations.REPLACE) {
            Object wrongValue = valueGenerator.generateValue(key, entrySize.next(random), random);
            Boolean replaced = (Boolean) stressor.makeRequest(new CacheInvocations.Replace(conditionalCache, key,
                  shouldMatch ? oldValue : wrongValue, newValue));
            matchSelector.record(replaced);
         } else if (operation == ConditionalOperations.REPLACE_ANY) {
            Object prevValue;
            if (shouldMatch) {
               prevValue = stressor.makeRequest(new CacheInvocations.ReplaceAny(conditionalCache, key, newValue));
               matchSelector.record((Boolean) prevValue);
            } else {
               prevValue = stressor.makeRequest(new CacheInvocations.PutIfAbsent(conditionalCache, key, newValue));
               matchSelector.record(prevValue == null);
            }
         } else if (operation == ConditionalOperations.GET_AND_REPLACE) {
            Object prevValue;
            if (shouldMatch) {
               prevValue = stressor.makeRequest(new CacheInvocations.GetAndReplace(conditionalCache, key, newValue));
               matchSelector.record(oldValue.equals(prevValue));
            } else {
               prevValue = stressor.makeRequest(new CacheInvocations.PutIfAbsent(conditionalCache, key, newValue));
               matchSelector.record(prevValue == null);
            }
         } else throw new IllegalStateException("Unable to execute operation: " + operation.name);
      }
   }
}
