package org.radargun.stages.cache.test.async;

import java.util.concurrent.CompletableFuture;

import org.radargun.Operation;
import org.radargun.stages.test.async.Conversation;
import org.radargun.stats.Statistics;
import org.radargun.traits.BasicAsyncOperations;
import org.radargun.traits.Transactional;

/**
 * Immutable share-able class describing a conversation.
 */
public class ComposedConversation implements Conversation {
   private final Operation operation;
   private final ConversationStep[] steps;
   private final ContextProvider provider;
   private final boolean transactional;

   public ComposedConversation(Operation operation, ConversationStep[] steps, ContextProvider provider, boolean transactional) {
      this.operation = operation;
      this.steps = steps;
      this.provider = provider;
      this.transactional = transactional;
   }

   @Override
   public void start(Statistics stats) {
      BasicAsyncOperations.Cache cache = provider.cache();
      Transactional.Transaction tx = null;
      if (transactional) {
         tx = provider.transaction();
         cache = tx.wrap(cache);
         // begin is usually very fast; don't record operation for that
         tx.begin();
      }
      ConversationContext ctx = new ConversationContext(stats, this, cache, tx);
      CompletableFuture<?> cf = steps[0].apply(ctx);
      if (tx != null) {
         tx.suspend();
      }
      cf.whenComplete(ctx);
   }

   public Operation operation() {
      return operation;
   }

   public ContextProvider provider() {
      return provider;
   }

   public int size() {
      return steps.length;
   }


   public ConversationStep step(int index) {
      return steps[index];
   }
}
