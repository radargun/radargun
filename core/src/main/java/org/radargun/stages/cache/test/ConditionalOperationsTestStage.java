package org.radargun.stages.cache.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.radargun.Operation;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.InjectTrait;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
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

   protected OperationSelector operationSelector;

   @Init
   public void init() {
      operationSelector = new OperationSelector.Builder()
            .add(ConditionalOperations.REMOVE, removeRatio)
            .add(ConditionalOperations.REPLACE, replaceRatio)
            .add(ConditionalOperations.REPLACE_ANY, replaceAnyRatio)
            .add(ConditionalOperations.GET_AND_REPLACE, getAndReplaceRatio)
            .build();
      statisticsPrototype.registerOperationsGroup(ConditionalOperations.class.getSimpleName() + ".Total",
                                                  new HashSet<>(Arrays.asList(
                                                        ConditionalOperations.REMOVE,
                                                        ConditionalOperations.REPLACE,
                                                        ConditionalOperations.REPLACE_ANY,
                                                        ConditionalOperations.GET_AND_REPLACE
                                                  )));
      statisticsPrototype.registerOperationsGroup(ConditionalOperations.class.getSimpleName() + ".Total.TX",
                                                  new HashSet<>(Arrays.asList(
                                                        Invocations.Remove.TX,
                                                        Invocations.Replace.TX,
                                                        Invocations.ReplaceAny.TX,
                                                        Invocations.GetAndReplace.TX
                                                  )));
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
      public void init(Stressor stressor) {
         super.init(stressor);
         String cacheName = cacheSelector.getCacheName(stressor.getGlobalThreadIndex());
         nonTxBasicCache = basicOperations.getCache(cacheName);
         nonTxConditionalCache = conditionalOperations.getCache(cacheName);
         if (!useTransactions(cacheName)) {
            basicCache = nonTxBasicCache;
            conditionalCache = nonTxConditionalCache;
         }
         stressor.setUseTransactions(useTransactions(cacheName));
         keySelector = getKeySelector(stressor);
      }

      @Override
      public void transactionStarted() {
         basicCache = stressor.wrap(nonTxBasicCache);
         conditionalCache = stressor.wrap(nonTxConditionalCache);
      }

      @Override
      public void transactionEnded() {
         basicCache = null;
         conditionalCache = null;
      }

      @Override
      public Object run() throws RequestException {
         Random random = stressor.getRandom();
         Object key = keyGenerator.generateKey(keySelector.next());
         Operation operation = operationSelector.next(random);
         Object newValue = valueGenerator.generateValue(key, entrySize.next(random), random);
         boolean shouldMatch = matchSelector.shouldMatch();

         Object oldValue = stressor.makeRequest(new Invocations.Get(basicCache, key));

         if (oldValue == null) {
            Object prevValue;
            if (shouldMatch) {
               prevValue = stressor.makeRequest(new Invocations.PutIfAbsent(conditionalCache, key, newValue));
               matchSelector.record(prevValue == null);
            } else {
               prevValue = stressor.makeRequest(new Invocations.GetAndReplace(conditionalCache, key, newValue));
               matchSelector.record(prevValue != null);
            }
            return prevValue;
         } else if (operation == ConditionalOperations.REMOVE) {
            Boolean removed = (Boolean) stressor.makeRequest(new Invocations.RemoveConditionally(conditionalCache, key,
                  shouldMatch ? oldValue : newValue));
            matchSelector.record(removed);
            return removed;
         } else if (operation == ConditionalOperations.REPLACE) {
            Object wrongValue = valueGenerator.generateValue(key, entrySize.next(random), random);
            Boolean replaced = (Boolean) stressor.makeRequest(new Invocations.Replace(conditionalCache, key,
                  shouldMatch ? oldValue : wrongValue, newValue));
            matchSelector.record(replaced);
            return replaced;
         } else if (operation == ConditionalOperations.REPLACE_ANY) {
            Object prevValue;
            if (shouldMatch) {
               prevValue = stressor.makeRequest(new Invocations.ReplaceAny(conditionalCache, key, newValue));
               matchSelector.record((Boolean) prevValue);
            } else {
               prevValue = stressor.makeRequest(new Invocations.PutIfAbsent(conditionalCache, key, newValue));
               matchSelector.record(prevValue == null);
            }
            return prevValue;
         } else if (operation == ConditionalOperations.GET_AND_REPLACE) {
            Object prevValue;
            if (shouldMatch) {
               prevValue = stressor.makeRequest(new Invocations.GetAndReplace(conditionalCache, key, newValue));
               matchSelector.record(oldValue.equals(prevValue));
            } else {
               prevValue = stressor.makeRequest(new Invocations.PutIfAbsent(conditionalCache, key, newValue));
               matchSelector.record(prevValue == null);
            }
            return prevValue;
         } else throw new IllegalStateException("Unable to execute operation: " + operation.name);
      }
   }
}
