package org.radargun.stages;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.radargun.MultimapInvocations;
import org.radargun.Operation;
import org.radargun.Version;
import org.radargun.config.Init;
import org.radargun.config.Namespace;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.cache.test.CacheOperationsTestStage;
import org.radargun.stages.cache.test.KeySelector;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.OperationSelector;
import org.radargun.stages.test.RatioOperationSelector;
import org.radargun.stages.test.Stressor;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.MultimapCacheOperations;

@Stage(doc = "Test using MiltimapCacheOperations")
@Namespace(name = MultimapCacheOperationsTestStage.NAMESPACE)
public class MultimapCacheOperationsTestStage extends CacheOperationsTestStage {

   public static final String NAMESPACE = "urn:radargun:stages:multimap:" + Version.SCHEMA_VERSION;

   @Property(doc = "MultimapCache name.")
   protected String multimapCacheName;

   @Property(doc = "Defines the number of the values saved under same key. Default is 1.")
   protected int numValuesPerKey = 1;

   @Property(doc = "Ratio of GET requests. Default is 4.")
   protected int getRatio = 4;

   @Property(doc = "Ratio of PUT requests. Default is 1.")
   protected int putRatio = 1;

   @Property(doc = "Ratio of CONTAINS_KEY requests. Default is 0.")
   protected int containsKeyRatio = 0;

   @Property(doc = "Ratio of CONTAINS_VALUE requests. Default is 0.")
   protected int containsValueRatio = 0;

   @Property(doc = "Ratio of CONTAINS_ENTRY requests. Default is 0.")
   protected int containsEntryRatio = 0;

   @Property(doc = "Ratio of REMOVE_BY_KEY_VALUE requests. Default is 0.")
   protected int removeByKeyValueRatio = 0;

   @Property(doc = "Ratio of REMOVE_BY_PREDICATE requests. Default is 0.")
   protected int removeByPredicateRatio = 0;

   @Property(doc = "Ratio of REMOVE requests. Default is 0.")
   protected int removeRatio = 0;

   @Property(doc = "Ratio of SIZE requests. Default is 0.")
   protected int sizeRatio = 0;

   @InjectTrait
   protected MultimapCacheOperations multimapCacheOperations;

   @Init
   @Override
   public void init() {
      super.init();
      statisticsPrototype.registerOperationsGroup(
         MultimapCacheOperations.class.getSimpleName() + ".Total",
         new HashSet<>(
            Arrays.asList(
               MultimapCacheOperations.GET,
               MultimapCacheOperations.PUT,
               MultimapCacheOperations.CONTAINS_KEY,
               MultimapCacheOperations.CONTAINS_VALUE,
               MultimapCacheOperations.CONTAINS_ENTRY,
               MultimapCacheOperations.REMOVE,
               MultimapCacheOperations.REMOVE_BY_KEY_VALUE,
               MultimapCacheOperations.REMOVE_BY_PREDICATE,
               MultimapCacheOperations.SIZE
            )
         )
      );
   }

   @Override
   protected OperationSelector createOperationSelector() {
      return new RatioOperationSelector.Builder()
         .add(MultimapCacheOperations.GET, getRatio)
         .add(MultimapCacheOperations.PUT, putRatio)
         .add(MultimapCacheOperations.REMOVE, removeRatio)
         .add(MultimapCacheOperations.REMOVE_BY_KEY_VALUE, removeByKeyValueRatio)
         .add(MultimapCacheOperations.REMOVE_BY_PREDICATE, removeByPredicateRatio)
         .add(MultimapCacheOperations.CONTAINS_KEY, containsKeyRatio)
         .add(MultimapCacheOperations.CONTAINS_VALUE, containsValueRatio)
         .add(MultimapCacheOperations.CONTAINS_ENTRY, containsEntryRatio)
         .add(MultimapCacheOperations.SIZE, sizeRatio)
         .build();
   }

   @Override
   public OperationLogic getLogic() {
      return new MultimapLogic();
   }

   /**
    * The type Multimap logic.
    */
   protected class MultimapLogic extends OperationLogic {
      protected MultimapCacheOperations.MultimapCache multimapCache;
      protected KeySelector keySelector;


      @Override
      public void init(Stressor stressor) {
         super.init(stressor);
         if (multimapCacheName == null || multimapCacheName.isEmpty()) {
            multimapCacheName = multimapCacheOperations.getCacheName();
         }
         multimapCache = multimapCacheOperations.getMultimapCache(multimapCacheName);
         stressor.setUseTransactions(false); // current MultimapCache<K, V> implementation doesn't support transactions
         keySelector = getKeySelector(stressor);
      }


      @Override
      public void run(Operation operation) throws RequestException {
         Object key = keyGenerator.generateKey(keySelector.next());
         Random random = stressor.getRandom();

         if (operation == MultimapCacheOperations.GET) {
            stressor.makeRequest(new MultimapInvocations.Get(multimapCache, key));
         } else if (operation == MultimapCacheOperations.PUT) {
            for (int i = 0; i < numValuesPerKey; i++) {
               stressor.makeRequest(new MultimapInvocations.Put(multimapCache, key, valueGenerator.generateValue(key, entrySize.next(random), random)));
            }
         } else if (operation == MultimapCacheOperations.REMOVE) {
            stressor.makeRequest(new MultimapInvocations.Remove(multimapCache, key));
         } else if (operation == MultimapCacheOperations.REMOVE_BY_KEY_VALUE) {
            stressor.makeRequest(new MultimapInvocations.RemoveByKeyValue(multimapCache, key, valueGenerator.generateValue(key, entrySize.next(random), random)));
         } else if (operation == MultimapCacheOperations.REMOVE_BY_PREDICATE) {
            Object value = valueGenerator.generateValue(key, entrySize.next(random), random);
            stressor.makeRequest(new MultimapInvocations.RemoveByPredicate(
               multimapCache, p -> p.equals(value)
            ));
         } else if (operation == MultimapCacheOperations.CONTAINS_KEY) {
            stressor.makeRequest(new MultimapInvocations.ContainsKey(multimapCache, key));
         } else if (operation == MultimapCacheOperations.CONTAINS_VALUE) {
            stressor.makeRequest(new MultimapInvocations.ContainsValue(multimapCache, valueGenerator.generateValue(key, entrySize.next(random), random)));
         } else if (operation == MultimapCacheOperations.CONTAINS_ENTRY) {
            stressor.makeRequest(new MultimapInvocations.ContainsEntry(multimapCache, key, valueGenerator.generateValue(key, entrySize.next(random), random)));
         } else if (operation == MultimapCacheOperations.SIZE) {
            stressor.makeRequest(new MultimapInvocations.Size(multimapCache));
         } else {
            throw new IllegalArgumentException(operation.name);
         }
      }
   }
}

