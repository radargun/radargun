package org.radargun.stages.cache.background;

import java.util.*;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stages.helpers.Range;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * Divides key space used by {@link org.radargun.stages.cache.background.Stressor}s into multiple segments, corresponding
 * to key range defined by {@link org.radargun.stages.cache.background.AbstractLogLogic} implementations.
 * Furthermore, it keeps track of currently processed key and operation performed by {@link org.radargun.stages.cache.background.LogChecker}.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class StressorRecord {

   protected static final Log log = LogFactory.getLog(StressorRecord.class);
   protected static final boolean trace = log.isTraceEnabled();
   protected final Random rand;
   protected Range keyRange;
   protected final int threadId;
   protected long currentKeyId;
   protected volatile long currentOp = -1;
   protected final LinkedList<StressorConfirmation> confirmations = new LinkedList<>();
   private long lastUnsuccessfulCheckTimestamp = Long.MIN_VALUE;
   private long lastSuccessfulCheckTimestamp = TimeService.currentTimeMillis();
   private Set<Long> notifiedOps = new HashSet<>();
   private long requireNotify = Long.MAX_VALUE;

   public StressorRecord(int threadId, Range keyRange) {
      this.rand = new Random(threadId);
      log.trace("Initializing record random with " + Utils.getRandomSeed(rand));
      this.threadId = threadId;
      this.keyRange = keyRange;
      privateNext();
   }

   public StressorRecord(StressorRecord record, long operationId, long seed) {
      this.rand = Utils.setRandomSeed(new Random(0), seed);
      log.trace("Initializing record random with " + seed);
      this.threadId = record.threadId;
      this.currentOp = operationId;
      this.keyRange = record.keyRange;
      privateNext();
   }

   public void next() {
      privateNext();
   }

   // avoid calling overridable method in constructor, as object can be found in an inconsistent state
   private void privateNext() {
      currentKeyId = keyRange.getStart() + (rand.nextLong() & Long.MAX_VALUE) % keyRange.getSize();
      checkFinished(currentOp++);
   }

   public String getStatus() {
      return String.format("thread=%d, lastStressorOperation=%d, currentOp=%d, currentKeyId=%08X, notifiedOps=%s, requireNotify=%d, " +
                                 "lastSuccessfulCheckTimestamp=%d, lastUnsuccessfulCheckTimestamp=%d.",
            threadId, getLastConfirmedOperationId(), currentOp, currentKeyId, notifiedOps, requireNotify,
            lastSuccessfulCheckTimestamp, lastUnsuccessfulCheckTimestamp);
   }

   public Object getLastConfirmedOperationId() {
      synchronized (confirmations) {
         return confirmations.isEmpty() ? -1 : confirmations.getLast().operationId;
      }
   }

   public int getThreadId() {
      return threadId;
   }

   public long getLastUnsuccessfulCheckTimestamp() {
      return lastUnsuccessfulCheckTimestamp;
   }

   public void setLastUnsuccessfulCheckTimestamp(long lastUnsuccessfulCheckTimestamp) {
      this.lastUnsuccessfulCheckTimestamp = lastUnsuccessfulCheckTimestamp;
   }

   public long getKeyId() {
      return currentKeyId;
   }

   public long getOperationId() {
      return currentOp;
   }

   public Set<Long> getNotifiedOps() {
      return notifiedOps;
   }

   public long getRequireNotify() {
      return requireNotify;
   }

   public Random getRand() {
      return rand;
   }

   public synchronized void notify(long operationId, Object key) {
      if (operationId < currentOp || !notifiedOps.add(operationId)) {
         log.warn("Duplicit notification for operation " + operationId + " on key " + key);
      }
   }

   public void checkFinished(long operationId) {
      // remove old confirmations
      synchronized (confirmations) {
         Iterator<StressorConfirmation> iterator = confirmations.iterator();
         while (iterator.hasNext()) {
            StressorConfirmation confirmation = iterator.next();
            if (confirmation.operationId > operationId) {
               break;
            }
            iterator.remove();
         }
      }
      // remove old notifications
      synchronized (this) {
         notifiedOps.remove(operationId);
      }
   }

   public synchronized void requireNotify(long operationId) {
      if (operationId < requireNotify) {
         requireNotify = operationId;
      }
   }

   public synchronized boolean hasNotification(long operationId) {
      if (operationId < requireNotify) return true;
      return notifiedOps.contains(operationId);
   }

   public void setLastSuccessfulCheckTimestamp(long timestamp) {
      this.lastSuccessfulCheckTimestamp = timestamp;
   }

   public long getLastSuccessfulCheckTimestamp() {
      return lastSuccessfulCheckTimestamp;
   }

   public void addConfirmation(long operationId, long timestamp) {
      try {
         synchronized (confirmations) {
            ListIterator<StressorConfirmation> iterator = confirmations.listIterator(confirmations.size());
            if (trace) {
               log.tracef("Confirmations for thread %d were %s", threadId, confirmations);
            }
            while (iterator.hasPrevious()) {
               StressorConfirmation confirmation = iterator.previous();
               if (confirmation.operationId < operationId) {
                  // Move index one position to the right, as it was shifted by iterator.previous()
                  iterator.next();
                  confirmations.add(iterator.nextIndex(), new StressorConfirmation(operationId, timestamp));
                  return;
               } else if (confirmation.operationId == operationId) {
                  return;
               }
            }
            if (confirmations.isEmpty()) {
               confirmations.add(new StressorConfirmation(operationId, timestamp));
            }
         }
      } finally {
         if (trace) {
            log.tracef("Confirmations for thread %d are %s", threadId, confirmations);
         }
      }
   }

   /**
    * @return Epoch time (ms) timestamp or negative value if not confirmed yet.
    */
   public long getCurrentConfirmationTimestamp() {
      synchronized (confirmations) {
         ListIterator<StressorConfirmation> iterator = confirmations.listIterator(confirmations.size());
         while (iterator.hasPrevious()) {
            StressorConfirmation confirmation = iterator.previous();
            if (confirmation.operationId > currentOp) {
               return confirmation.timestamp;
            }
         }
         return -1;
      }
   }

   public static class StressorConfirmation {
      public final long operationId;
      public final long timestamp;

      public StressorConfirmation(long operationId, long timestamp) {
         this.operationId = operationId;
         this.timestamp = timestamp;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         StressorConfirmation that = (StressorConfirmation) o;

         if (operationId != that.operationId) return false;
         return timestamp == that.timestamp;

      }

      @Override
      public int hashCode() {
         int result = (int) (operationId ^ (operationId >>> 32));
         result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
         return result;
      }

      @Override
      public String toString() {
         return "StressorConfirmation{" +
               "operationId=" + operationId +
               ", timestamp=" + timestamp +
               '}';
      }
   }
}
