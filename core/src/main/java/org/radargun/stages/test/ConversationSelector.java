package org.radargun.stages.test;

/**
 * Decides which {@link Conversation} should be executed next.
 */
public interface ConversationSelector {
   Conversation next() throws InterruptedException;
}
