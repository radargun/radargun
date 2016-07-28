package org.radargun.stages.cache.test;

import java.util.Random;

import org.radargun.Operation;
import org.radargun.config.PropertyDelegate;
import org.radargun.config.Stage;
import org.radargun.stages.test.AbstractConversation;
import org.radargun.stages.test.Conversation;
import org.radargun.stages.test.SchedulingSelector;
import org.radargun.stages.test.Stressor;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;

@Stage(doc = "Test using BasicOperations")
public class BasicOperationsTestSetupStage extends CacheTestSetupStage {

   @PropertyDelegate(prefix = "get.")
   protected InvocationSetting get = new InvocationSetting();

   @PropertyDelegate(prefix = "getTx.")
   protected TxInvocationSetting getTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "containsKey.")
   protected InvocationSetting containsKey = new InvocationSetting();

   @PropertyDelegate(prefix = "containsKeyTx.")
   protected TxInvocationSetting containsKeyTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "put.")
   protected InvocationSetting put = new InvocationSetting();

   @PropertyDelegate(prefix = "putTx.")
   protected TxInvocationSetting putTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "getAndPut.")
   protected InvocationSetting getAndPut = new InvocationSetting();

   @PropertyDelegate(prefix = "getAndPutTx.")
   protected TxInvocationSetting getAndPutTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "remove.")
   protected InvocationSetting remove = new InvocationSetting();

   @PropertyDelegate(prefix = "removeTx.")
   protected TxInvocationSetting removeTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "getAndRemove.")
   protected InvocationSetting getAndRemove = new InvocationSetting();

   @PropertyDelegate(prefix = "getAndRemoveTx.")
   protected TxInvocationSetting getAndRemoveTx = new TxInvocationSetting();

   @InjectTrait
   protected BasicOperations basicOperations;

   @InjectTrait
   protected Transactional transactional;

   private BasicOperations.Cache<Object, Object> cache;

   @Override
   protected SchedulingSelector<Conversation> createSelector() {
      return new SchedulingSelector.Builder<>(Conversation.class)
         .add(new Get(), get.invocations, get.interval)
         .add(new GetTx(), getTx.invocations, getTx.interval)
         .add(new ContainsKey(), containsKey.invocations, containsKey.interval)
         .add(new ContainsKeyTx(), containsKeyTx.invocations, containsKeyTx.interval)
         .add(new Put(), put.invocations, put.interval)
         .add(new PutTx(), putTx.invocations, putTx.interval)
         .add(new GetAndPut(), getAndPut.invocations, getAndPut.interval)
         .add(new GetAndPutTx(), getAndPutTx.invocations, getAndPutTx.interval)
         .add(new Remove(), remove.invocations, remove.interval)
         .add(new RemoveTx(), removeTx.invocations, removeTx.interval)
         .add(new GetAndRemove(), getAndRemove.invocations, getAndRemove.interval)
         .add(new GetAndRemoveTx(), getAndRemoveTx.invocations, getAndRemoveTx.interval)
         .build();
   }

   @Override
   public void prepare() {
      super.prepare();
      cache = basicOperations.getCache(cacheName);
   }

   private abstract class TxConversation extends BaseTxConversation<BasicOperations.Cache> {
      public TxConversation(Operation txOperation, TxInvocationSetting invocationSetting) {
         super(txOperation, invocationSetting);
      }

      @Override
      protected BasicOperations.Cache getCache() {
         return cache;
      }

      @Override
      protected Transactional.Transaction getTransaction() {
         return transactional.getTransaction();
      }
   }

   private class Get extends AbstractConversation {
      @Override
      public void run(Stressor stressor) throws InterruptedException {
         stressor.makeRequest(new CacheInvocations.Get(cache, getRandomKey(stressor.getRandom())));
      }
   }

   private class GetTx extends TxConversation {
      public GetTx() {
         super(CacheInvocations.Get.TX, getTx);
      }

      @Override
      protected void invoke(Stressor stressor, BasicOperations.Cache cache, Random random) {
         stressor.makeRequest(new CacheInvocations.Get(cache, getRandomKey(random)));
      }
   }

   private class ContainsKey extends AbstractConversation {
      @Override
      public void run(Stressor stressor) throws InterruptedException {
         stressor.makeRequest(new CacheInvocations.ContainsKey(cache, getRandomKey(stressor.getRandom())));
      }
   }

   private class ContainsKeyTx extends TxConversation {
      public ContainsKeyTx() {
         super(CacheInvocations.ContainsKey.TX, containsKeyTx);
      }

      @Override
      protected void invoke(Stressor stressor, BasicOperations.Cache cache, Random random) {
         stressor.makeRequest(new CacheInvocations.ContainsKey(cache, getRandomKey(random)));
      }
   }

   private class Put extends AbstractConversation {
      @Override
      public void run(Stressor stressor) throws InterruptedException {
         Random random = stressor.getRandom();
         Object key = getRandomKey(random);
         stressor.makeRequest(new CacheInvocations.Put(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random)));
      }
   }

   private class PutTx extends TxConversation {
      public PutTx() {
         super(CacheInvocations.Put.TX, putTx);
      }

      @Override
      protected void invoke(Stressor stressor, BasicOperations.Cache cache, Random random) {
         Object key = getRandomKey(random);
         stressor.makeRequest(new CacheInvocations.Put(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random)));
      }
   }

   private class GetAndPut extends AbstractConversation {
      @Override
      public void run(Stressor stressor) throws InterruptedException {
         Random random = stressor.getRandom();
         Object key = getRandomKey(random);
         stressor.makeRequest(new CacheInvocations.GetAndPut(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random)));
      }
   }

   private class GetAndPutTx extends TxConversation {
      public GetAndPutTx() {
         super(CacheInvocations.GetAndPut.TX, getAndPutTx);
      }

      @Override
      protected void invoke(Stressor stressor, BasicOperations.Cache cache, Random random) {
         Object key = getRandomKey(random);
         stressor.makeRequest(new CacheInvocations.GetAndPut(cache, key, valueGenerator.generateValue(key, entrySize.next(random), random)));
      }
   }

   private class Remove extends AbstractConversation {
      @Override
      public void run(Stressor stressor) throws InterruptedException {
         stressor.makeRequest(new CacheInvocations.Remove(cache, getRandomKey(stressor.getRandom())));
      }
   }

   private class RemoveTx extends TxConversation {
      public RemoveTx() {
         super(CacheInvocations.Remove.TX, removeTx);
      }

      @Override
      protected void invoke(Stressor stressor, BasicOperations.Cache cache, Random random) {
         stressor.makeRequest(new CacheInvocations.Remove(cache, getRandomKey(random)));
      }
   }

   private class GetAndRemove extends AbstractConversation {
      @Override
      public void run(Stressor stressor) throws InterruptedException {
         stressor.makeRequest(new CacheInvocations.GetAndRemove(cache, getRandomKey(stressor.getRandom())));
      }
   }

   private class GetAndRemoveTx extends TxConversation {
      public GetAndRemoveTx() {
         super(CacheInvocations.GetAndRemove.TX, getAndRemoveTx);
      }

      @Override
      protected void invoke(Stressor stressor, BasicOperations.Cache cache, Random random) {
         stressor.makeRequest(new CacheInvocations.GetAndRemove(cache, getRandomKey(random)));
      }
   }
}

