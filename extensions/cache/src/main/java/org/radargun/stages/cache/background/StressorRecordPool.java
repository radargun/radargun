package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * A pool of {@link org.radargun.stages.cache.background.StressorRecord}s. Used by log checkers to ensure all stressor
 * records are handled in a fair way (once a stressor record is chcecked, it is returned to the pool in a FIFO fashion).
 */
public class StressorRecordPool {

   protected static final Log log = LogFactory.getLog(StressorRecordPool.class);

   private final int totalThreads;
   // Array of all stressor records
   private final AtomicReferenceArray<StressorRecord> allRecords;
   // Represents current state of pool
   private final ConcurrentLinkedQueue<StressorRecord> availableRecords = new ConcurrentLinkedQueue<>();
   private final StressorRecordNotifier stressorRecordNotifier;

   public StressorRecordPool(int totalThreads, List<StressorRecord> stressorRecords, BackgroundOpsManager manager) {
      this.totalThreads = totalThreads;
      this.allRecords = new AtomicReferenceArray<>(totalThreads);
      this.stressorRecordNotifier = new StressorRecordNotifier(manager);
      stressorRecordNotifier.registerListeners(true); // synchronous listeners
      init(stressorRecords);
   }

   private void init(List<StressorRecord> stressorRecords) {
      for (StressorRecord stressorRecord : stressorRecords) {
         availableRecords.add(stressorRecord);
         allRecords.set(stressorRecord.getThreadId(), stressorRecord);
      }
      log.tracef("Pool will contain %d records. Current state: %s", allRecords.length(), availableRecords);
   }

   public int getTotalThreads() {
      return totalThreads;
   }

   public AtomicReferenceArray<StressorRecord> getAllRecords() {
      return allRecords;
   }

   public Collection<StressorRecord> getAvailableRecords() {
      ArrayList<StressorRecord> records = new ArrayList<>();
      for (int i = 0; i < allRecords.length(); ++i) {
         records.add(allRecords.get(i));
      }
      return records;
   }

   public StressorRecord take() {
      return availableRecords.poll();
   }

   public void add(StressorRecord record) {
      availableRecords.add(record);
   }

}
