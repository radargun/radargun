package org.radargun.stages.test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.radargun.Operation;
import org.radargun.utils.TimeService;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PeriodicOperationSelector implements OperationSelector {
   protected final long requestPeriod;
   // prevents non-intended synchronization
   protected final ThreadLocal<Record> threadLocalRecord = new ThreadLocal<>();
   protected final OperationSelector operationSelector;
   protected boolean started = false;
   protected long startTime = -1;


   public PeriodicOperationSelector(OperationSelector operationSelector, long requestPeriod) {
      this.requestPeriod = TimeUnit.MILLISECONDS.toNanos(requestPeriod);
      this.operationSelector = operationSelector;
   }

   @Override
   public synchronized void start() {
      if (!started) {
         startTime = TimeService.nanoTime();
         started = true;
      }
      operationSelector.start();
   }

   @Override
   public Operation next(Random random) {
      Record record = threadLocalRecord.get();
      if (record == null) {
         record = new Record(new Random(Thread.currentThread().getId() ^ TimeService.nanoTime()).nextLong() % requestPeriod);
         threadLocalRecord.set(record);
      }
      long waitTime = TimeUnit.NANOSECONDS.toMillis(startTime + record.rampUp + requestPeriod * record.opNumber - TimeService.nanoTime());
      // for times < 1ms, do not wait
      if (waitTime > 0) {
         try {
            Thread.sleep(waitTime);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }
      record.opNumber++;
      return operationSelector.next(random);
   }

   private static class Record {
      private final long rampUp;
      private int opNumber;

      public Record(long rampUp) {
         this.rampUp = rampUp;
      }
   }
}
