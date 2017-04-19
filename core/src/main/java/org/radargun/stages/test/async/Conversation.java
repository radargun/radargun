package org.radargun.stages.test.async;

import org.radargun.stats.Statistics;

/**
 * Represents a sequence of asynchronous invocations
 */
public interface Conversation {
   /**
    * Start executing the sequence. This method should be immediately return.
    *
    * @param stats Statistics where this conversation should be recorded. This must allow concurrent access,
    *              conversation is not required to enforce any particular thread to complete the request.
    */
   void start(Statistics stats);

   interface Selector {
      Conversation next();
   }
}
