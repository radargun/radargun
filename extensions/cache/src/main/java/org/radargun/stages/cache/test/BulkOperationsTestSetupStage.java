package org.radargun.stages.cache.test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.radargun.Operation;
import org.radargun.config.Property;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.stages.test.AbstractConversation;
import org.radargun.stages.test.Conversation;
import org.radargun.stages.test.SchedulingSelector;
import org.radargun.stages.test.Stressor;
import org.radargun.traits.BulkOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;

@Stage(doc = "Executes operations from BulkOperations trait.")
public class BulkOperationsTestSetupStage extends CacheTestSetupStage {
   @Property(doc = "Number of keys inserted/retrieved within one operation. Default is 10.")
   protected int bulkSize = 10;

   @PropertyDelegate(prefix = "getAllNative.")
   protected InvocationSetting getAllNative = new InvocationSetting();

   @PropertyDelegate(prefix = "getAllNativeTx.")
   protected TxInvocationSetting getAllNativeTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "getAllAsync.")
   protected InvocationSetting getAllAsync = new InvocationSetting();

   @PropertyDelegate(prefix = "getAllAsyncTx.")
   protected TxInvocationSetting getAllAsyncTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "putAllNative.")
   protected InvocationSetting putAllNative = new InvocationSetting();

   @PropertyDelegate(prefix = "putAllNativeTx.")
   protected TxInvocationSetting putAllNativeTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "putAllAsync.")
   protected InvocationSetting putAllAsync = new InvocationSetting();

   @PropertyDelegate(prefix = "putAllAsyncTx.")
   protected TxInvocationSetting putAllAsyncTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "removeAllNative.")
   protected InvocationSetting removeAllNative = new InvocationSetting();

   @PropertyDelegate(prefix = "removeAllNativeTx.")
   protected TxInvocationSetting removeAllNativeTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "removeAllAsync.")
   protected InvocationSetting removeAllAsync = new InvocationSetting();

   @PropertyDelegate(prefix = "removeAllAsyncTx.")
   protected TxInvocationSetting removeAllAsyncTx = new TxInvocationSetting();

   @InjectTrait
   protected BulkOperations bulkOperations;

   @InjectTrait
   protected Transactional transactional;

   private BulkOperations.Cache<Object, Object> nativeCache;
   private BulkOperations.Cache<Object, Object> asyncCache;

   @Override
   protected void prepare() {
      super.prepare();
      nativeCache = bulkOperations.getCache(cacheName, false);
      asyncCache = bulkOperations.getCache(cacheName, false);
   }

   @Override
   protected SchedulingSelector<Conversation> createSelector() {
      return new SchedulingSelector.Builder<>(Conversation.class)
         .add(new GetAll(false), getAllNative.invocations, getAllNative.interval)
         .add(new GetAllTx(false, getAllNativeTx), getAllNativeTx.invocations, getAllNativeTx.interval)
         .add(new GetAll(false), getAllAsync.invocations, getAllAsync.interval)
         .add(new GetAllTx(false, getAllAsyncTx), getAllAsyncTx.invocations, getAllAsyncTx.interval)
         .add(new PutAll(false), putAllNative.invocations, putAllNative.interval)
         .add(new PutAllTx(false, putAllNativeTx), putAllNativeTx.invocations, putAllNativeTx.interval)
         .add(new PutAll(false), putAllAsync.invocations, putAllAsync.interval)
         .add(new PutAllTx(false, putAllAsyncTx), putAllAsyncTx.invocations, putAllAsyncTx.interval)
         .add(new RemoveAll(false), removeAllNative.invocations, removeAllNative.interval)
         .add(new RemoveAllTx(false, removeAllNativeTx), removeAllNativeTx.invocations, removeAllNativeTx.interval)
         .add(new RemoveAll(false), removeAllAsync.invocations, removeAllAsync.interval)
         .add(new RemoveAllTx(false, removeAllAsyncTx), removeAllAsyncTx.invocations, removeAllAsyncTx.interval)
         .build();
   }

   private Map getRandomKeyValues(Stressor stressor) {
      Map map = new HashMap<>(bulkSize);
      Random random = stressor.getRandom();
      for (int i = 0; i < bulkSize; ) {
         Object key = getRandomKey(random);
         if (!map.containsKey(key)) {
            map.put(key, valueGenerator.generateValue(key, entrySize.next(random), random));
            ++i;
         }
      }
      return map;
   }

   private Set getRandomKeys(Stressor stressor) {
      Set set = new HashSet<>(bulkSize);
      Random random = stressor.getRandom();
      for (int i = 0; i < bulkSize; ) {
         Object key = getRandomKey(random);
         if (set.add(key)) {
            ++i;
         }
      }
      return set;
   }

   private abstract class NonTxConversation extends AbstractConversation {
      protected final boolean async;
      protected final BulkOperations.Cache cache;

      public NonTxConversation(boolean async) {
         this.async = async;
         this.cache = async ? asyncCache : nativeCache;
      }
   }

   private abstract class TxConversation extends BaseTxConversation<BulkOperations.Cache> {
      protected final boolean async;
      protected final BulkOperations.Cache cache;

      public TxConversation(Operation txOperation, TxInvocationSetting invocationSetting, boolean async) {
         super(txOperation, invocationSetting);
         this.async = async;
         this.cache = async ? asyncCache : nativeCache;
      }

      @Override
      protected BulkOperations.Cache getCache() {
         return cache;
      }

      @Override
      protected Transactional.Transaction getTransaction() {
         return transactional.getTransaction();
      }
   }

   private class GetAll extends NonTxConversation {
      public GetAll(boolean async) {
         super(async);
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         stressor.makeRequest(new CacheInvocations.GetAll(cache, async, getRandomKeys(stressor)));
      }
   }

   private class GetAllTx extends TxConversation {
      public GetAllTx(boolean async, TxInvocationSetting txInvocations) {
         super(CacheInvocations.Get.TX, txInvocations, async);
      }

      @Override
      protected void invoke(Stressor stressor, BulkOperations.Cache cache, Random random) {
         stressor.makeRequest(new CacheInvocations.GetAll(cache, async, getRandomKeys(stressor)));
      }
   }

   private class PutAll extends NonTxConversation {
      public PutAll(boolean async) {
         super(async);
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         stressor.makeRequest(new CacheInvocations.PutAll(nativeCache, async, getRandomKeyValues(stressor)));
      }
   }

   private class PutAllTx extends TxConversation {
      private final boolean async;

      public PutAllTx(boolean async, TxInvocationSetting txInvocations) {
         super(CacheInvocations.Get.TX, txInvocations, async);
         this.async = async;
      }

      @Override
      protected void invoke(Stressor stressor, BulkOperations.Cache cache, Random random) {
         stressor.makeRequest(new CacheInvocations.PutAll(cache, async, getRandomKeyValues(stressor)));
      }
   }

   private class RemoveAll extends NonTxConversation {
      public RemoveAll(boolean async) {
         super(async);
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         stressor.makeRequest(new CacheInvocations.RemoveAll(nativeCache, async, getRandomKeys(stressor)));
      }
   }

   private class RemoveAllTx extends TxConversation {
      private final boolean async;

      public RemoveAllTx(boolean async, TxInvocationSetting txInvocations) {
         super(CacheInvocations.Get.TX, txInvocations, async);
         this.async = async;
      }

      @Override
      protected void invoke(Stressor stressor, BulkOperations.Cache cache, Random random) {
         stressor.makeRequest(new CacheInvocations.RemoveAll(cache, async, getRandomKeys(stressor)));
      }
   }
}
