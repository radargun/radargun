package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Transactional;

/**
 * Stage for testing guaranties of isolation levels.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage for testing guaranties of isolation levels.")
public class IsolationLevelCheckStage extends CheckStage {

   private static final String ISOLATION_CHECK_KEY = "isolationCheckKey";
   public static final String REPEATABLE_READ = "REPEATABLE_READ";
   public static final String READ_COMMITTED = "READ_COMMITTED";

   @Property(doc = "Number of concurrent threads that modify the value. Default is 2.")
   private int writers = 2;

   @Property(doc = "Number of concurrent threads that try to retrieve the value. Default is 10.")
   private int readers = 10;

   @Property(converter = TimeConverter.class, doc = "How long should this stage take. Default is 1 minute.")
   private long duration = 60000;

   @Property(doc = "Number of reads executed inside on transaction. Default is 30.")
   private int transactionSize = 30;

   @Property(optional = false, doc = "Expected isolation level (should match to cache configuration). Supported " +
         "values are [" + IsolationLevelCheckStage.READ_COMMITTED + ", " + IsolationLevelCheckStage.REPEATABLE_READ + "]")
   private String expectedLevel;

   private volatile boolean finished;
   private volatile boolean valueChangeDetected = false;

   @InjectTrait
   private BasicOperations basicOperations;

   @InjectTrait
   private Transactional transactional;

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      if (expectedLevel == null) {
         return exception(ack, "No expected level set", null);
      } else if (!expectedLevel.equalsIgnoreCase(REPEATABLE_READ) && !expectedLevel.equalsIgnoreCase(READ_COMMITTED)) {
         return exception(ack, "Expected level should be one of " + READ_COMMITTED + " and " + REPEATABLE_READ, null);
      }
      BasicOperations.Cache<Object, Object> cache = basicOperations.getCache(null);
      try {
         cache.put(ISOLATION_CHECK_KEY, new Long(0));
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
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      if (!super.processAckOnMaster(acks)) return false;

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

   private class WriterThread extends ClientThread {
      @Override
      public void run() {
         BasicOperations.Cache cache = basicOperations.getCache(null);
         Transactional.Resource txCache = transactional.getResource(null);
         Random rand = new Random();
         while (!finished) {
            try {
               log.trace("Starting transaction");
               txCache.startTransaction();
               cache.put(ISOLATION_CHECK_KEY, -1);
               Thread.sleep(10);
               long value = rand.nextInt(1000);
               cache.put(ISOLATION_CHECK_KEY, value);
               log.trace("Inserted value " + value);
               txCache.endTransaction(true);
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
         BasicOperations.Cache cache = basicOperations.getCache(null);
         Transactional.Resource txCache = transactional.getResource(null);
         while (!finished) {
            log.trace("Starting transaction");
            txCache.startTransaction();
            Object lastValue = null;
            for (int i = 0; i < transactionSize; ++i) {
               try {
                  Object value = cache.get(ISOLATION_CHECK_KEY);
                  log.trace("Read value " + value + ", previous value is " + lastValue);
                  if (!(value instanceof Long) || ((Long) value) < 0) {
                     exception = new IllegalStateException("Unexpected value " + value);
                     txCache.endTransaction(false);
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
            txCache.endTransaction(true);
            log.trace("Ended transaction");
         }
      }
   }
}
