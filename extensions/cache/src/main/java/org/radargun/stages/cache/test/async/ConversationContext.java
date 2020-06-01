package org.radargun.stages.cache.test.async;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.radargun.stats.Request;
import org.radargun.stats.RequestSet;
import org.radargun.stats.Statistics;
import org.radargun.traits.BasicAsyncOperations;
import org.radargun.traits.Transactional;

/**
 * Mutable state of a conversation, handling steps return and transaction end. Note that the transaction
 * is started and first step invoked in {@link ComposedConversation#start(Statistics)}.
 */
class ConversationContext implements BiConsumer<Object, Throwable> {
   private final Statistics stats;
   private final RequestSet requestSet;
   private final ComposedConversation conversation;
   private final BasicAsyncOperations.Cache cache;
   private final Transactional.Transaction tx;
   private int index = 1; // first operation is executed
   // TODO: we might record the randomly selected keys and allow conversations steps
   // to refer to previously used ones

   public ConversationContext(Statistics stats, ComposedConversation conversation, BasicAsyncOperations.Cache cache, Transactional.Transaction tx) {
      this.stats = stats;
      this.requestSet = stats.requestSet();
      this.conversation = conversation;
      this.cache = cache;
      this.tx = tx;
   }

   public Statistics statistics() {
      return stats;
   }

   public RequestSet requestSet() {
      return requestSet;
   }

   public ContextProvider provider() {
      return conversation.provider();
   }

   public BasicAsyncOperations.Cache cache() {
      return cache;
   }

   @Override
   public void accept(Object o, Throwable throwable) {
      try {
         if (throwable == null) {
            int currentIndex = index;
            if (currentIndex < conversation.size()) {
               index = currentIndex + 1;
               if (tx != null) {
                  tx.resume();
               }
               CompletableFuture<?> cf = conversation.step(currentIndex).apply(this);
               if (tx != null) {
                  tx.suspend();
               }
               cf.whenComplete(this);
            } else {
               if (tx != null) {
                  // If this is the stressor thread, we can't block it. If this is invoked by a internal thread,
                  // we probably can block.
                  if (Thread.currentThread().getThreadGroup() == provider().stressorGroup()) {
                     Request request = statistics().startRequest();
                     // we include the time to switch to the executor within the commit time
                     provider().executor().execute(() -> {
                        commit(request);
                     });
                  } else {
                     commit(statistics().startRequest());
                  }
               } else {
                  requestSet.succeeded(conversation.operation());
               }
            }
         } else {
            provider().log().error("Failed to execute conversation", throwable);
            if (tx != null) {
               if (Thread.currentThread().getThreadGroup() == provider().stressorGroup()) {
                  Request request = statistics().startRequest();
                  provider().executor().execute(() -> rollback(request));
               } else {
                  rollback(statistics().startRequest());
               }
            } else {
               requestSet.failed(conversation.operation());
            }
         }
      } catch (Throwable t) {
         provider().log().error("Unexpected exception", t);
      }
   }

   private void commit(Request commitRequest) {
      try {
         tx.commit();
         commitRequest.succeeded(Transactional.COMMIT);
      } catch (Throwable t) {
         provider().log().error("Failed to commit transaction", t);
         commitRequest.failed(Transactional.COMMIT);
      } finally {
         requestSet.add(commitRequest);
         if (commitRequest.isSuccessful()) {
            requestSet.succeeded(conversation.operation());
         } else {
            requestSet.failed(conversation.operation());
         }
      }
   }

   private void rollback(Request rollbackRequest) {
      try {
         tx.rollback();
         rollbackRequest.succeeded(Transactional.ROLLBACK);
      } catch (Throwable t) {
         provider().log().error("Failed to rollback transaction");
         rollbackRequest.failed(Transactional.ROLLBACK);
      } finally {
         requestSet.add(rollbackRequest);
         requestSet.failed(conversation.operation());
      }
   }
}
