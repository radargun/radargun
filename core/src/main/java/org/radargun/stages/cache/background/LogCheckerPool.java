package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheListeners;

/**
 * TODO document
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class LogCheckerPool implements CacheListeners.UpdatedListener, CacheListeners.CreatedListener {

   protected static final Log log = LogFactory.getLog(LogCheckerPool.class);

   private final int totalThreads;
   private final AtomicReferenceArray<StressorRecord> allRecords;
   private final ConcurrentLinkedQueue<StressorRecord> records = new ConcurrentLinkedQueue<StressorRecord>();
   private final BackgroundOpsManager manager;
   private final AtomicLong missingOperations = new AtomicLong();
   private final AtomicLong missingNotifications = new AtomicLong();

   public LogCheckerPool(int totalThreads, List<StressorRecord> stressorRecords, BackgroundOpsManager manager) {
      this.totalThreads = totalThreads;
      this.allRecords = new AtomicReferenceArray<>(totalThreads);
      this.manager = manager;
      for (StressorRecord stressorRecord: stressorRecords) {
         addNew(stressorRecord);
      }
      log.trace("Pool will contain " + allRecords.length() + records);
      registerListeners(true); // synchronous listeners
   }

   public LogCheckerPool(int totalThreads, BackgroundOpsManager manager) {
      this.totalThreads = totalThreads;
      this.allRecords = new AtomicReferenceArray<>(totalThreads);
      log.trace("Pool will contain " + allRecords.length() + records);
      this.manager = manager;
   }

   protected void registerListeners(boolean sync) {
      if (!manager.getLogLogicConfiguration().isCheckNotifications()) {
         return;
      }
      CacheListeners listeners = manager.getListeners();
      if (listeners == null) {
         throw new IllegalArgumentException("Service does not support cache listeners");
      }
      Collection<CacheListeners.Type> supported = listeners.getSupportedListeners();
      if (!supported.containsAll(Arrays.asList(CacheListeners.Type.CREATED, CacheListeners.Type.UPDATED))) {
         throw new IllegalArgumentException("Service does not support required listener types; supported are: " + supported);
      }
      String cacheName = manager.getGeneralConfiguration().getCacheName();
      manager.getListeners().addCreatedListener(cacheName, this, sync);
      manager.getListeners().addUpdatedListener(cacheName, this, sync);
   }

   public long getMissingOperations() {
      return missingOperations.get();
   }

   public long getMissingNotifications() {
      return missingNotifications.get();
   }

   public void reportMissingOperation() {
      missingOperations.incrementAndGet();
   }

   public void reportMissingNotification() {
      missingNotifications.incrementAndGet();
   }

   public int getTotalThreads() {
      return totalThreads;
   }

   public Collection<StressorRecord> getRecords() {
      ArrayList<StressorRecord> records = new ArrayList<>();
      for (int i = 0; i < allRecords.length(); ++i) {
         records.add(allRecords.get(i));
      }
      return records;
   }

   public StressorRecord take() {
      return records.poll();
   }

   public void add(StressorRecord record) {
      records.add(record);
   }

   private void addNew(StressorRecord record) {
      records.add(record);
      allRecords.set(record.getThreadId(), record);
   }

   public String waitUntilChecked(long timeout) {
      for (int i = 0; i < totalThreads; ++i) {
         StressorRecord record = allRecords.get(i);
         if (record == null) continue;
         try {
            // as the pool survives service restarts, we have to always grab actual cache
            LogChecker.LastOperation lastOperation = (LogChecker.LastOperation) manager.getBasicCache().get(LogChecker.lastOperationKey(record.getThreadId()));
            if (lastOperation == null) {
               log.trace("Thread " + record.getThreadId() + " has no recorded operation.");
            } else {
               record.addConfirmation(lastOperation.getOperationId(), lastOperation.getTimestamp());
            }
         } catch (Exception e) {
            log.error("Failed to read last operation key for thread " + record.getThreadId(), e);
         }
      }
      for (;;) {
         boolean allChecked = true;
         long now = System.currentTimeMillis();
         for (int i = 0; i < totalThreads; ++i) {
            StressorRecord record = allRecords.get(i);
            if (record == null) continue;
            long confirmationTimestamp = record.getCurrentConfirmationTimestamp();
            if (confirmationTimestamp > 0) {
               if (log.isTraceEnabled()) {
                  log.trace(record.getStatus());
               }
               allChecked = false;
               break;
            }
            if (record.getLastSuccessfulCheckTimestamp() + timeout < now) {
               String error = "Waiting for checker for record [" + record.getStatus() + "] timed out after "
                     + (now - record.getLastSuccessfulCheckTimestamp()) + " ms";
               log.error(error);
               return error;
            }
         }
         if (allChecked) {
            StringBuilder sb = new StringBuilder("All checks OK: ");
            for (int i = 0; i < totalThreads; ++i) {
               StressorRecord record = allRecords.get(i);
               if (record == null) continue;
               sb.append(record.getThreadId()).append("# ")
                     .append(record.getOperationId()).append(" (")
                     .append(record.getLastConfirmedOperationId()).append("), ");
            }
            log.debug(sb.toString());
            return null;
         }
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
            log.error("Interrupted waiting for checkers.", e);
            return e.toString();
         }
      }
   }

   protected void notify(int threadId, long operationId, Object key) {
      StressorRecord record = allRecords.get(threadId);
      record.notify(operationId, key);
   }

   protected void requireNotify(int threadId, long operationId) {
      StressorRecord record = allRecords.get(threadId);
      record.requireNotify(operationId);
   }

   @Override
   public void created(Object key, Object value) {
      log.trace("Created " + key + " -> " + value);
      modified(key, value);
   }

   @Override
   public void updated(Object key, Object value) {
      log.trace("Updated " + key + " -> " + value);
      modified(key, value);
   }

   public void modified(Object key, Object value) {
      if (value instanceof PrivateLogValue) {
         PrivateLogValue logValue = (PrivateLogValue) value;
         notify(logValue.getThreadId(), logValue.getOperationId(logValue.size() - 1), key);
      } else if (value instanceof SharedLogValue) {
         SharedLogValue logValue = (SharedLogValue) value;
         int last = logValue.size() - 1;
         notify(logValue.getThreadId(last), logValue.getOperationId(last), key);
      } else if (key instanceof String && ((String) key).startsWith(LogChecker.LAST_OPERATION_PREFIX)) {
         int threadId = Integer.parseInt(((String) key).substring(LogChecker.LAST_OPERATION_PREFIX.length()));
         LogChecker.LastOperation last = (LogChecker.LastOperation) value;
         requireNotify(threadId, last.getOperationId() + 1);
      }
   }

}
