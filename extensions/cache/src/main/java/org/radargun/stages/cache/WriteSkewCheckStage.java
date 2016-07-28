package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.SlaveState;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;
import org.radargun.utils.TimeConverter;

/**
 * Stage checking the write skew detection in transactional caches.
 */
@Stage(doc = "Stage checking the write skew detection in transactional caches.")
public class WriteSkewCheckStage extends CheckStage {
   private static final String WRITE_SKEW_CHECK_KEY = "writeSkewCheckKey";

   @Property(converter = TimeConverter.class, doc = "Duration of the test. Default is 1 minute.")
   private long duration = 60000;

   @Property(doc = "Number of threads overwriting concurrently the entry. Default is 10.")
   private int threads = 10;

   @Property(doc = "Should write skew between null value and first value be tested? Default is false.")
   private boolean testNull = false;

   private volatile boolean finished = false;
   private AtomicLong totalCounter = new AtomicLong(0);
   private AtomicLong skewCounter = new AtomicLong(0);

   @InjectTrait
   private BasicOperations basicOperations;

   @InjectTrait
   private Transactional transactional;

   @Override
   public DistStageAck executeOnSlave() {
      BasicOperations.Cache cache = basicOperations.getCache(null);
      if (!testNull) {
         try {
            cache.put(WRITE_SKEW_CHECK_KEY, new Long(0));
         } catch (Exception e) {
            return errorResponse("Failed to insert initial zero", e);
         }
         try {
            Thread.sleep(30000);
         } catch (InterruptedException e) {
            log.warn("Sleep was interrupted");
         }
      }

      List<ClientThread> threadList = new ArrayList<ClientThread>();
      for (int i = 0; i < threads; ++i) {
         ClientThread t = new WriteSkewThread();
         t.start();
         threadList.add(t);
      }
      try {
         Thread.sleep(duration);
      } catch (InterruptedException e) {
         log.warn("Sleep was interrupted");
      }
      finished = true;
      DistStageAck error = checkThreads(threadList);
      if (error != null) return error;
      try {
         Thread.sleep(30000);
      } catch (InterruptedException e) {
         log.warn("Sleep was interrupted");
      }

      long ispnCounter;
      try {
         Object value = cache.get(WRITE_SKEW_CHECK_KEY);
         if (!(value instanceof Long)) {
            return errorResponse("Counter is not a long: it is " + value);
         } else {
            ispnCounter = (Long) value;
         }
      } catch (Exception e) {
         return errorResponse("Failed to insert first value", e);
      }
      return new Counters(slaveState, totalCounter.get(), skewCounter.get(), ispnCounter);
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      long sumIncrements = 0;
      long sumSkews = 0;
      long maxValue = -1;
      for (Counters ack : instancesOf(acks, Counters.class)) {
         sumIncrements += ack.totalCounter;
         sumSkews += ack.skewCounter;
         maxValue = Math.max(maxValue, ack.ispnCounter);
      }
      log.infof("Total increments: %d, Skews: %d, DB value: %d", sumIncrements, sumSkews, maxValue);
      if (maxValue + sumSkews != sumIncrements) {
         log.errorf("Database contains value %d but slaves report %d successful increments",
            maxValue, sumIncrements - sumSkews);
         return errorResult();
      } else {
         log.infof("Performed %d successful increments in %d ms", sumIncrements - sumSkews, duration);
         return StageResult.SUCCESS;
      }
   }

   private static class Counters extends DistStageAck {
      long totalCounter;
      long skewCounter;
      long ispnCounter;

      private Counters(SlaveState slaveState, long totalCounter, long skewCounter, long ispnCounter) {
         super(slaveState);
         this.totalCounter = totalCounter;
         this.skewCounter = skewCounter;
         this.ispnCounter = ispnCounter;
      }

      @Override
      public String toString() {
         return "Counters{" +
            "totalCounter=" + totalCounter +
            ", skewCounter=" + skewCounter +
            ", ispnCounter=" + ispnCounter +
            "} " + super.toString();
      }
   }

   private class WriteSkewThread extends ClientThread {
      @Override
      public void run() {
         BasicOperations.Cache nonTxCache = basicOperations.getCache(null);
         while (!finished) {
            Transactional.Transaction tx = transactional.getTransaction();
            BasicOperations.Cache txCache = tx.wrap(nonTxCache);
            log.trace("Starting transaction");
            tx.begin();
            Object value = null;
            try {
               value = txCache.get(WRITE_SKEW_CHECK_KEY);
               if (value == null) {
                  value = new Long(0);
               } else if (!(value instanceof Long)) {
                  exception = new IllegalStateException("Counter is not a long: it is " + value);
                  return;
               }
               txCache.put(WRITE_SKEW_CHECK_KEY, ((Long) value) + 1);
            } catch (Exception e) {
               exception = e;
               return;
            }
            boolean skew = false;
            try {
               tx.commit();
            } catch (Exception e) {
               log.trace("Skew detected");
               skewCounter.incrementAndGet();
               skew = true;
            }
            if (!skew && ((Long) value).longValue() == 0L) {
               log.trace("Successfully inserted 1");
            }
            totalCounter.incrementAndGet();
            log.trace("Ended transaction");
         }
      }
   }
}
