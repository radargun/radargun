package org.radargun.stages.test.async;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.radargun.DistStageAck;
import org.radargun.config.EnsureInSchema;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.test.BaseTestStage;
import org.radargun.stats.Statistics;
import org.radargun.stats.SynchronizedWrapper;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.TimeService;

@Stage(doc = "Base for asynchronous tests")
public abstract class AsyncTestStage extends BaseTestStage {

   @Property(doc = "Duration of ramp-up phase where the operations are not recorded. Default is 5 seconds", converter = TimeConverter.class)
   protected long rampUp = 5000;

   @Property(doc = "Number of threads that start conversations. This should be about equal to number of cores. Default is 8.")
   protected int numThreads = 8;

   @Property(doc = "Timeout to wait for the threads to join. Default is 1 second.", converter = TimeConverter.class)
   protected long threadJoinTimeout = 1000;

   private Thread[] threads;
   private Statistics[] stats;
   private volatile SchedulingSelector<Conversation> selector;
   private volatile boolean recording = false;
   private volatile boolean running = true;

   @Override
   public DistStageAck executeOnSlave() {
      prepare();
      if (!statisticsPrototype.isThreadSafe()) {
         statisticsPrototype = new SynchronizedWrapper(statisticsPrototype);
      }

      selector = createSelector();
      threads = new Thread[numThreads];
      stats = new Statistics[numThreads];
      for (int i = 0; i < numThreads; ++i) {
         Statistics recorded = stats[i] = statisticsPrototype.newInstance();
         Statistics rampUp = statisticsPrototype.newInstance();
         rampUp.begin(); // it is illegal to record into not started Statistics
         threads[i] = new Thread(() -> {
            while (running) {
               try {
                  Conversation conversation = selector.next();
                  conversation.start(recording ? recorded : rampUp);
               } catch (InterruptedException e) {
                  log.trace("Thread was interrupted", e);
               }
            }
         }, "stressor-" + i);
         threads[i].start();
      }

      try {
         Thread.sleep(rampUp);
         for (Statistics s : stats) {
            s.begin();
         }
         recording = true;
         // steady state
         Thread.sleep(duration);
         recording = false;
         for (Statistics s : stats) {
            s.end();
         }
         running = false;
         Thread.sleep(1000);
         for (Thread t : threads) t.interrupt();
         long deadline = TimeService.currentTimeMillis() + threadJoinTimeout;
         for (Thread t : threads) t.join(Math.max(1, deadline - TimeService.currentTimeMillis()));
         // There's no sense in distinguishing statistics from different threads as the threads don't represent
         // anything and the number of operations executed in each thread can vary a lot.
         List<Statistics> merged = Stream.of(stats).reduce(Statistics.MERGE)
               .map(Collections::singletonList).orElseGet(Collections::emptyList);
         return new StatisticsAck(slaveState, merged, statisticsPrototype.getGroupOperationsMap());
      } catch (InterruptedException e) {
         running = false;
         return errorResponse("Interrupted", e);
      }
   }

   protected abstract SchedulingSelector<Conversation> createSelector();

   @EnsureInSchema
   public static class InvocationSetting {
      @Property(doc = "Number of invocations of given operation per interval (see property interval), on each node. Default is 0.")
      public int invocations = 0;

      @Property(doc = "Size of the slot in milliseconds. Raising this risks having bursts" +
            "at the beginning of the interval. Default is 1 ms.", converter = TimeConverter.class)
      public long interval = 1;
   }

   @EnsureInSchema
   public static class TxInvocationSetting extends InvocationSetting {
      @Property(doc = "Number of operations in single transaction. Default is 1.")
      public int transactionSize = 1;

      @Property(doc = "Percentage of transactions committed (as opposed to rolled back). " +
            "Default is 1 - all transactions are committed.")
      public double commit = 1d;

      public boolean shouldCommit(Random random) {
         if (commit == 1d) return true;
         else if (commit == 0d) return false;
         else return random.nextDouble() < commit;
      }
   }
}
