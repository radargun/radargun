package org.radargun.stages.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
      "values are [" + IsolationLevelCheckStage.READ_COMMITTED + ", " + IsolationLevelCheckStage.REPEATABLE_READ + "].")
   private String expectedLevel;

   private volatile boolean finished;
   private volatile boolean valueChangeDetected = false;

   @InjectTrait
   private BasicOperations basicOperations;

   @InjectTrait
   private Transactional transactional;

   @Override
   public DistStageAck executeOnSlave() {
      if (expectedLevel == null) {
         return errorResponse("No expected level set", null);
      } else if (!expectedLevel.equalsIgnoreCase(REPEATABLE_READ) && !expectedLevel.equalsIgnoreCase(READ_COMMITTED)) {
         return errorResponse("Expected level should be one of " + READ_COMMITTED + " and " + REPEATABLE_READ, null);
      }
      BasicOperations.Cache<Object, Object> cache = basicOperations.getCache(null);
      try {
         cache.put(ISOLATION_CHECK_KEY, new Long(0));
      } catch (Exception e) {
         return errorResponse("Failed to insert first value", e);
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
         log.error("Thread has been interrupted", e);
         Thread.currentThread().interrupt();
      }
      finished = true;
      DistStageAck error = checkThreads(threads);
      if (error != null) return error;
      return new ChangeAck(slaveState, valueChangeDetected);
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      StageResult result = super.processAckOnMaster(acks);
      if (result.isError()) return result;

      boolean anyValueChangeDetected = false;
      for (ChangeAck ack : instancesOf(acks, ChangeAck.class)) {
         log.debugf("Value change detected on slave %d: %s", ack.getSlaveIndex(), ack.valueChangeDetected);
         if (expectedLevel.equalsIgnoreCase(REPEATABLE_READ) && ack.valueChangeDetected) {
            log.error("Value change was detected but this should not happen with isolation " + expectedLevel);
            return errorResult();
         }
         anyValueChangeDetected |= ack.valueChangeDetected;
      }
      if (expectedLevel.equalsIgnoreCase(READ_COMMITTED) && !anyValueChangeDetected) {
         log.error("Value change was expected with isolation " + expectedLevel + " but none was detected");
         return errorResult();
      }
      return StageResult.SUCCESS;
   }

   private static class ChangeAck extends DistStageAck {
      final boolean valueChangeDetected;

      private ChangeAck(SlaveState slaveState, boolean valueChangeDetected) {
         super(slaveState);
         this.valueChangeDetected = valueChangeDetected;
      }
   }

   private class WriterThread extends ClientThread {
      @Override
      public void run() {
         BasicOperations.Cache nonTxCache = basicOperations.getCache(null);
         Random rand = new Random();
         while (!finished) {
            try {
               Transactional.Transaction tx = transactional.getTransaction();
               BasicOperations.Cache txCache = tx.wrap(nonTxCache);
               log.trace("Starting transaction");
               tx.begin();
               txCache.put(ISOLATION_CHECK_KEY, -1);
               Thread.sleep(10);
               long value = rand.nextInt(1000);
               txCache.put(ISOLATION_CHECK_KEY, value);
               log.trace("Inserted value " + value);
               tx.commit();
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
         BasicOperations.Cache nonTxCache = basicOperations.getCache(null);
         while (!finished) {
            Transactional.Transaction tx = transactional.getTransaction();
            BasicOperations.Cache txCache = tx.wrap(nonTxCache);
            log.trace("Starting transaction");
            tx.begin();
            Object lastValue = null;
            for (int i = 0; i < transactionSize; ++i) {
               try {
                  Object value = txCache.get(ISOLATION_CHECK_KEY);
                  log.trace("Read value " + value + ", previous value is " + lastValue);
                  if (!(value instanceof Long) || ((Long) value) < 0) {
                     exception = new IllegalStateException("Unexpected value " + value);
                     tx.rollback();
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
            tx.commit();
            log.trace("Ended transaction");
         }
      }
   }
}
