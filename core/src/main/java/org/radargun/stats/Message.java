package org.radargun.stats;

import java.util.concurrent.TimeUnit;

import org.radargun.Operation;

/**
 * Tracks time for operations with different origin and destination nodes.
 * The time can be tracked only using wall-clock time.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Message {
   private final Statistics statistics;
   private long sendStartTime = Long.MAX_VALUE;
   // private long sendCompleteTime; // hard to track when the message itself carries the timestamps
   // private long receiveStartTime;
   private long receiveCompleteTime = Long.MIN_VALUE;

   public Message(Statistics statistics) {
      this.statistics = statistics;
   }

   public Message times(long sendStartTime, long receiveCompleteTime) {
      this.sendStartTime = sendStartTime;
      this.receiveCompleteTime = receiveCompleteTime;
      return this;
   }

   public long getSendStartTime() {
      return sendStartTime;
   }

   public void record(Operation operation) {
      statistics.record(this, operation);
   }

   public void discard() {
      statistics.discard(this);
   }

   public boolean isValid() {
      return sendStartTime < receiveCompleteTime;
   }

   public long totalTime() {
      return TimeUnit.MILLISECONDS.toNanos(receiveCompleteTime - sendStartTime);
   }

   public long getReceiveCompleteTime() {
      return receiveCompleteTime;
   }
}
