package org.radargun.stages.test;

/**
 * Decides which {@link Conversation} should be executed next.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface ConversationSelector {
   Conversation next() throws InterruptedException;
}
