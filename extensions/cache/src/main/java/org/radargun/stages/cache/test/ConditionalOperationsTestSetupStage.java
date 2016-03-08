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
import org.radargun.traits.ConditionalOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Tests (atomic) conditional operations. Note that there is no put-if-absent-ratio" +
   "- this operation is executed anytime the selected key does not have value.")
public class ConditionalOperationsTestSetupStage extends CacheTestSetupStage {
   protected static final Operation PUT_IF_ABSENT_OR_REMOVE_TX = Operation.register("PUT_IF_ABSENT_OR_REMOVE_TX");
   protected static final Operation PUT_IF_ABSENT_OR_REPLACE_TX = Operation.register("PUT_IF_ABSENT_OR_REPLACE_TX");
   protected static final Operation PUT_IF_ABSENT_OR_REPLACE_ANY_TX = Operation.register("PUT_IF_ABSENT_OR_REPLACE_ANY_TX");
   protected static final Operation PUT_IF_ABSENT_OR_GET_AND_REPLACE_TX = Operation.register("PUT_IF_ABSENT_OR_GET_AND_REPLACE_TX");
   protected static final Operation FAIL_PUT_IF_ABSENT_OR_REMOVE_TX = Operation.register("FAIL_PUT_IF_ABSENT_OR_REMOVE_TX");
   protected static final Operation FAIL_PUT_IF_ABSENT_OR_REPLACE_TX = Operation.register("FAIL_PUT_IF_ABSENT_OR_REPLACE_TX");
   protected static final Operation FAIL_PUT_IF_ABSENT_OR_REPLACE_ANY_TX = Operation.register("FAIL_PUT_IF_ABSENT_OR_REPLACE_ANY_TX");
   protected static final Operation FAIL_PUT_IF_ABSENT_OR_GET_AND_REPLACE_TX = Operation.register("FAIL_PUT_IF_ABSENT_OR_GET_AND_REPLACE_TX");


   @PropertyDelegate(prefix = "putIfAbsentOrRemove.")
   protected InvocationSetting putIfAbsentOrRemove = new InvocationSetting();

   @PropertyDelegate(prefix = "putIfAbsentOrRemoveTx.")
   protected TxInvocationSetting putIfAbsentOrRemoveTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "putIfAbsentOrReplace.")
   protected InvocationSetting putIfAbsentOrReplace = new InvocationSetting();

   @PropertyDelegate(prefix = "putIfAbsentOrReplaceTx.")
   protected TxInvocationSetting putIfAbsentOrReplaceTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "putIfAbsentOrReplaceAny.")
   protected InvocationSetting putIfAbsentOrReplaceAny = new InvocationSetting();

   @PropertyDelegate(prefix = "putIfAbsentOrReplaceAnyTx.")
   protected TxInvocationSetting putIfAbsentOrReplaceAnyTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "putIfAbsentOrGetAndReplace.")
   protected InvocationSetting putIfAbsentOrGetAndReplace = new InvocationSetting();

   @PropertyDelegate(prefix = "putIfAbsentOrGetAndReplaceTx.")
   protected TxInvocationSetting putIfAbsentOrGetAndReplaceTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrRemove.")
   protected InvocationSetting failPutIfAbsentOrRemove = new InvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrRemoveTx.")
   protected TxInvocationSetting failPutIfAbsentOrRemoveTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrReplace.")
   protected InvocationSetting failPutIfAbsentOrReplace = new InvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrReplaceTx.")
   protected TxInvocationSetting failPutIfAbsentOrReplaceTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrReplaceAny.")
   protected InvocationSetting failPutIfAbsentOrReplaceAny = new InvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrReplaceAnyTx.")
   protected TxInvocationSetting failPutIfAbsentOrReplaceAnyTx = new TxInvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrGetAndReplace.")
   protected InvocationSetting failPutIfAbsentOrGetAndReplace = new InvocationSetting();

   @PropertyDelegate(prefix = "failPutIfAbsentOrGetAndReplaceTx.")
   protected TxInvocationSetting failPutIfAbsentOrGetAndReplaceTx = new TxInvocationSetting();

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected BasicOperations basicOperations;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   protected ConditionalOperations conditionalOperations;

   @InjectTrait
   protected Transactional transactional;

   private BasicOperations.Cache<Object, Object> basicCache;
   private ConditionalOperations.Cache<Object, Object> conditionalCache;

   @Override
   protected void prepare() {
      super.prepare();
      basicCache = basicOperations.getCache(cacheName);
      conditionalCache = conditionalOperations.getCache(cacheName);
   }

   protected SchedulingSelector<Conversation> createSelector() {
      return new SchedulingSelector.Builder<>(Conversation.class)
         .add(new PutIfAbsentOrRemove(false), putIfAbsentOrRemove.invocations, putIfAbsentOrRemove.interval)
         .add(new PutIfAbsentOrRemoveTx(putIfAbsentOrRemoveTx, false), putIfAbsentOrRemoveTx.invocations, putIfAbsentOrRemoveTx.interval)
         .add(new PutIfAbsentOrReplace(false), putIfAbsentOrReplace.invocations, putIfAbsentOrReplace.interval)
         .add(new PutIfAbsentOrReplaceTx(putIfAbsentOrReplaceTx, false), putIfAbsentOrReplaceTx.invocations, putIfAbsentOrReplaceTx.interval)
         .add(new PutIfAbsentOrReplaceAny(false), putIfAbsentOrReplaceAny.invocations, putIfAbsentOrReplaceAny.interval)
         .add(new PutIfAbsentOrReplaceAnyTx(putIfAbsentOrReplaceAnyTx, false), putIfAbsentOrReplaceAnyTx.invocations, putIfAbsentOrReplaceAnyTx.interval)
         .add(new PutIfAbsentOrGetAndReplace(false), putIfAbsentOrGetAndReplace.invocations, putIfAbsentOrGetAndReplace.interval)
         .add(new PutIfAbsentOrGetAndReplaceTx(putIfAbsentOrGetAndReplaceTx, false), putIfAbsentOrGetAndReplaceTx.invocations, putIfAbsentOrGetAndReplaceTx.interval)
         .add(new PutIfAbsentOrRemove(true), failPutIfAbsentOrRemove.invocations, failPutIfAbsentOrRemove.interval)
         .add(new PutIfAbsentOrRemoveTx(failPutIfAbsentOrRemoveTx, true), failPutIfAbsentOrRemoveTx.invocations, failPutIfAbsentOrRemoveTx.interval)
         .add(new PutIfAbsentOrReplace(true), failPutIfAbsentOrReplace.invocations, failPutIfAbsentOrReplace.interval)
         .add(new PutIfAbsentOrReplaceTx(failPutIfAbsentOrReplaceTx, true), failPutIfAbsentOrReplaceTx.invocations, failPutIfAbsentOrReplaceTx.interval)
         .add(new PutIfAbsentOrReplaceAny(true), failPutIfAbsentOrReplaceAny.invocations, failPutIfAbsentOrReplaceAny.interval)
         .add(new PutIfAbsentOrReplaceAnyTx(failPutIfAbsentOrReplaceAnyTx, true), failPutIfAbsentOrReplaceAnyTx.invocations, failPutIfAbsentOrReplaceAnyTx.interval)
         .add(new PutIfAbsentOrGetAndReplace(true), failPutIfAbsentOrGetAndReplace.invocations, failPutIfAbsentOrGetAndReplace.interval)
         .add(new PutIfAbsentOrGetAndReplaceTx(failPutIfAbsentOrGetAndReplaceTx, true), failPutIfAbsentOrGetAndReplaceTx.invocations, failPutIfAbsentOrGetAndReplaceTx.interval)
         .build();
   }

   private abstract class NonTxConversation extends AbstractConversation {
      protected final boolean fail;

      public NonTxConversation(boolean fail) {
         this.fail = fail;
      }
   }

   private abstract class TxConversation extends BaseTxConversation<ConditionalOperations.Cache> {
      protected final boolean fail;

      public TxConversation(Operation txOperation, TxInvocationSetting invocationSetting, boolean fail) {
         super(txOperation, invocationSetting);
         this.fail = fail;
      }

      @Override
      protected ConditionalOperations.Cache getCache() {
         return conditionalCache;
      }

      @Override
      protected Transactional.Transaction getTransaction() {
         return transactional.getTransaction();
      }
   }

   private void runPutIfAbsentOrRemove(Stressor stressor, Random random, boolean fail) {
      Object key = getRandomKey(random);
      Object oldValue = stressor.makeRequest(new CacheInvocations.Get(basicCache, key));
      if ((oldValue == null) != fail) {
         Object newValue = valueGenerator.generateValue(key, entrySize.next(random), random);
         stressor.makeRequest(new CacheInvocations.PutIfAbsent(conditionalCache, key, newValue));
      } else {
         stressor.makeRequest(new CacheInvocations.RemoveConditionally(conditionalCache, key, oldValue));
      }
   }

   private void runPutIfAbsentOrReplace(Stressor stressor, Random random, boolean fail) {
      Object key = getRandomKey(random);
      Object oldValue = stressor.makeRequest(new CacheInvocations.Get(basicCache, key));
      Object newValue = valueGenerator.generateValue(key, entrySize.next(random), random);
      if ((oldValue == null) != fail) {
         stressor.makeRequest(new CacheInvocations.PutIfAbsent(conditionalCache, key, newValue));
      } else {
         stressor.makeRequest(new CacheInvocations.Replace(conditionalCache, key, oldValue, newValue));
      }
   }

   private void runPutIfAbsentOrReplaceAny(Stressor stressor, Random random, boolean fail) {
      Object key = getRandomKey(random);
      Object oldValue = stressor.makeRequest(new CacheInvocations.Get(basicCache, key));
      Object newValue = valueGenerator.generateValue(key, entrySize.next(random), random);
      if ((oldValue == null) != fail) {
         stressor.makeRequest(new CacheInvocations.PutIfAbsent(conditionalCache, key, newValue));
      } else {
         stressor.makeRequest(new CacheInvocations.ReplaceAny(conditionalCache, key, newValue));
      }
   }

   private void runPutIfAbsentOrGetAndReplace(Stressor stressor, Random random, boolean fail) {
      Object key = getRandomKey(random);
      Object oldValue = stressor.makeRequest(new CacheInvocations.Get(basicCache, key));
      Object newValue = valueGenerator.generateValue(key, entrySize.next(random), random);
      if ((oldValue == null) != fail) {
         stressor.makeRequest(new CacheInvocations.PutIfAbsent(conditionalCache, key, newValue));
      } else {
         stressor.makeRequest(new CacheInvocations.GetAndReplace(conditionalCache, key, newValue));
      }
   }

   private class PutIfAbsentOrRemove extends NonTxConversation {
      public PutIfAbsentOrRemove(boolean fail) {
         super(fail);
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         runPutIfAbsentOrRemove(stressor, stressor.getRandom(), fail);
      }

   }

   private class PutIfAbsentOrRemoveTx extends TxConversation {
      public PutIfAbsentOrRemoveTx(TxInvocationSetting txInvocationSetting, boolean fail) {
         super(fail ? FAIL_PUT_IF_ABSENT_OR_REMOVE_TX : PUT_IF_ABSENT_OR_REMOVE_TX, txInvocationSetting, fail);
      }

      @Override
      protected void invoke(Stressor stressor, ConditionalOperations.Cache cache, Random random) {
         runPutIfAbsentOrRemove(stressor, random, fail);
      }
   }

   private class PutIfAbsentOrReplace extends NonTxConversation {
      public PutIfAbsentOrReplace(boolean fail) {
         super(fail);
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         Random random = stressor.getRandom();
         runPutIfAbsentOrReplace(stressor, random, fail);
      }
   }

   private class PutIfAbsentOrReplaceTx extends TxConversation {
      public PutIfAbsentOrReplaceTx(TxInvocationSetting txInvocationSetting, boolean fail) {
         super(fail ? FAIL_PUT_IF_ABSENT_OR_REPLACE_TX : PUT_IF_ABSENT_OR_REPLACE_TX, txInvocationSetting, fail);
      }

      @Override
      protected void invoke(Stressor stressor, ConditionalOperations.Cache cache, Random random) {
         runPutIfAbsentOrReplace(stressor, random, fail);
      }
   }

   private class PutIfAbsentOrReplaceAny extends NonTxConversation {
      public PutIfAbsentOrReplaceAny(boolean fail) {
         super(fail);
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         Random random = stressor.getRandom();
         runPutIfAbsentOrReplaceAny(stressor, random, fail);
      }
   }

   private class PutIfAbsentOrReplaceAnyTx extends TxConversation {
      public PutIfAbsentOrReplaceAnyTx(TxInvocationSetting txInvocationSetting, boolean fail) {
         super(fail ? FAIL_PUT_IF_ABSENT_OR_REPLACE_ANY_TX : PUT_IF_ABSENT_OR_REPLACE_ANY_TX, txInvocationSetting, fail);
      }

      @Override
      protected void invoke(Stressor stressor, ConditionalOperations.Cache cache, Random random) {
         runPutIfAbsentOrReplaceAny(stressor, random, fail);
      }
   }

   private class PutIfAbsentOrGetAndReplace extends NonTxConversation {
      public PutIfAbsentOrGetAndReplace(boolean fail) {
         super(fail);
      }

      @Override
      public void run(Stressor stressor) throws InterruptedException {
         Random random = stressor.getRandom();
         runPutIfAbsentOrGetAndReplace(stressor, random, fail);
      }
   }

   private class PutIfAbsentOrGetAndReplaceTx extends TxConversation {
      public PutIfAbsentOrGetAndReplaceTx(TxInvocationSetting txInvocationSetting, boolean fail) {
         super(fail ? FAIL_PUT_IF_ABSENT_OR_GET_AND_REPLACE_TX : PUT_IF_ABSENT_OR_GET_AND_REPLACE_TX, txInvocationSetting, fail);
      }

      @Override
      protected void invoke(Stressor stressor, ConditionalOperations.Cache cache, Random random) {
         runPutIfAbsentOrGetAndReplace(stressor, random, fail);
      }
   }
}
