package org.radargun.stages;

import org.radargun.CacheWrapper;
import org.radargun.DistStageAck;
import org.radargun.state.MasterState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class IsolationLevelCheckStage extends CheckStage {

   private static final String ISOLATION_CHECK_KEY = "isolationCheckKey";
   public static final String REPEATABLE_READ = "REPEATABLE_READ";
   public static final String READ_COMMITTED = "READ_COMMITTED";

   private int writers = 2;
   private int readers = 10;
   private long duration = 60000;
   private int transactionSize = 30;
   private String expectedLevel;

   private volatile boolean finished;
   private volatile boolean valueChangeDetected = false;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (expectedLevel == null) {
         return exception(ack, "No expected level set", null);
      } else if (!expectedLevel.equalsIgnoreCase(REPEATABLE_READ) && !expectedLevel.equalsIgnoreCase(READ_COMMITTED)) {
         return exception(ack, "Expected level should be one of " + READ_COMMITTED + " and " + REPEATABLE_READ, null);
      }
      CacheWrapper wrapper = slaveState.getCacheWrapper();
      try {
         wrapper.put(null, ISOLATION_CHECK_KEY, new Long(0));
      } catch (Exception e) {
         exception(ack, "Failed to insert first value", e);
         return ack;
      }
      List<ClientThread> threads = new ArrayList<ClientThread>();
      for (int i = 0; i < writers; ++i) {
         ClientThread t = new WriterThread();
         t.setName("Writer-" + i);
         t.start();
         threads.add(t);
      }
      for (int i = 0; i < readers; ++i) {
         ClientThread t = new ReaderThread();
         t.setName("Reader-" + i);
         t.start();
         threads.add(t);
      }
      try {
         Thread.sleep(duration);
      } catch (InterruptedException e) {
      }
      finished = true;
      if (!checkThreads(ack, threads)) return ack;
      ack.setPayload(valueChangeDetected);
      return ack;
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      if (!super.processAckOnMaster(acks, masterState)) return false;

      boolean anyValueChangeDetected = false;
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         boolean valueChangeDetected = (Boolean) dack.getPayload();
         log.debug(String.format("Value change detected on slave %d: %s", dack.getSlaveIndex(), valueChangeDetected));
         if (expectedLevel.equalsIgnoreCase(REPEATABLE_READ) && valueChangeDetected) {
            log.error("Value change was detected but this should not happen with isolation " + expectedLevel);
            return false;
         }
         anyValueChangeDetected |= valueChangeDetected;
      }
      if (expectedLevel.equalsIgnoreCase(READ_COMMITTED) && !anyValueChangeDetected) {
         log.error("Value change was expected with isolation " + expectedLevel + " but none was detected");
         return false;
      }
      return true;
   }

   public void setWriters(int writers) {
      this.writers = writers;
   }

   public void setReaders(int readers) {
      this.readers = readers;
   }

   public void setDuration(long duration) {
      this.duration = duration;
   }

   public void setTransactionSize(int transactionSize) {
      this.transactionSize = transactionSize;
   }

   public void setExpectedLevel(String expectedLevel) {
      this.expectedLevel = expectedLevel;
   }

   private class WriterThread extends ClientThread {
      @Override
      public void run() {
         CacheWrapper wrapper = slaveState.getCacheWrapper();
         Random rand = new Random();
         while (!finished) {
            try {
               log.trace("Starting transaction");
               wrapper.startTransaction();
               wrapper.put(null, ISOLATION_CHECK_KEY, -1);
               Thread.sleep(10);
               long value = rand.nextInt(1000);
               wrapper.put(null, ISOLATION_CHECK_KEY, value);
               log.trace("Inserted value " + value);
               wrapper.endTransaction(true);
               log.trace("Ended transaction");
            } catch (Exception e) {
               exception = e;
               return;
            }
         }
      }
   }

   private class ReaderThread extends ClientThread {
      @Override
      public void run() {
         CacheWrapper wrapper = slaveState.getCacheWrapper();
         while (!finished) {
            log.trace("Starting transaction");
            wrapper.startTransaction();
            Object lastValue = null;
            for (int i = 0; i < transactionSize; ++i) {
               try {
                  Object value = wrapper.get(null, ISOLATION_CHECK_KEY);
                  log.trace("Read value " + value + ", previous value is " + lastValue);
                  if (!(value instanceof Long) || ((Long) value) < 0) {
                     exception = new IllegalStateException("Unexpected value " + value);
                     wrapper.endTransaction(false);
                     return;
                  }
                  if (lastValue != null && !lastValue.equals(value)) {
                     log.trace("Change detected");
                     valueChangeDetected = true;
                  }
                  lastValue = value;
                  Thread.sleep(10);
               } catch (Exception e) {
                  exception = e;
                  return;
               }
            }
            wrapper.endTransaction(true);
            log.trace("Ended transaction");
         }
      }
   }
}
