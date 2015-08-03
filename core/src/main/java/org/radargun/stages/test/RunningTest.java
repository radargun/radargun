package org.radargun.stages.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.state.ServiceListenerAdapter;
import org.radargun.stats.Statistics;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * State of test (threads, synchronization objects etc..) that is created by {@link TestSetupStage}
 * and stored in {@link org.radargun.state.SlaveState} for the duration of the test, which usually
 * spans several stages (on or more pairs of {@link TestSetupStage} and {@link TestStage} and possibly
 * {@link FinishTestStage}).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RunningTest extends ServiceListenerAdapter {
   private final Log log = LogFactory.getLog(RunningTest.class);
   // Queue used by Stressors to report theirs statistics
   private final ConcurrentLinkedQueue<Statistics> statisticsQueue = new ConcurrentLinkedQueue<>();
   // All current stressors
   private final ArrayList<Stressor> stressors = new ArrayList<>();
   // Timestamp when last Stressor was created
   private final AtomicLong lastCreated = new AtomicLong(Long.MIN_VALUE);
   // Last conversation executed by current thread
   private final ThreadLocal<Conversation> lastConversation = new ThreadLocal<>();
   // Number of currently executed conversations (per conversation)
   private final ConcurrentMap<Conversation, AtomicInteger> actuallyExecuting = new ConcurrentHashMap<>();
   // Operations invoked when the test is stopped
   private final ArrayList<Runnable> stopListeners = new ArrayList<>();
   // Number of threads that are waiting for next conversation
   private final AtomicInteger waitingThreads = new AtomicInteger();
   // Number of stressors
   private final AtomicInteger threadCounter = new AtomicInteger();
   // Number of created statistics (cloning the prototype). Eventually all of them should end up in statisticsQueue
   private final AtomicInteger statisticsCounter = new AtomicInteger();

   // State when the stats should be recorded
   private volatile boolean steadyState = false;
   // Flag for graceful termination
   private volatile boolean finished = false;
   // Flag for non-graceful termination
   private volatile boolean terminated = false;
   // Set to true if we have attempted to use more than maxThreads
   private volatile boolean reachedMax = false;

   private volatile ConversationSelector selector;

   private Statistics statisticsPrototype;
   private int minWaitingThreads;
   private int maxThreads;
   private boolean logTransactionExceptions;
   private long minThreadCreationDelay;

   public static String nameFor(String testName) {
      return RunningTest.class.getName() + "." + testName;
   }

   public void setStatisticsPrototype(Statistics statisticsPrototype) {
      this.statisticsPrototype = statisticsPrototype;
   }

   public boolean isSteadyState() {
      return steadyState;
   }

   public void setSteadyState(boolean steadyState) {
      this.steadyState = steadyState;
   }

   public boolean isFinished() {
      return finished;
   }

   public boolean isTerminated() {
      return terminated;
   }

   public void setTerminated() {
      finished = true;
      terminated = true;
      steadyState = false;
   }

   public boolean isReachedMax() {
      return reachedMax;
   }

   public Statistics createStatistics() {
      if (statisticsPrototype == null) {
         return null;
      } else {
         statisticsCounter.incrementAndGet();
         return statisticsPrototype.newInstance();
      }
   }

   public void recordStatistics(Statistics stats) {
      statisticsQueue.add(stats);
   }

   public List<Statistics> getStatistics() {
      interruptStressors();
      ArrayList<Statistics> statistics = new ArrayList<>();
      while (!terminated && statistics.size() < statisticsCounter.get()) {
         Statistics stats = statisticsQueue.poll();
         if (stats != null) {
            statistics.add(stats);
         } else {
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               throw new IllegalStateException(e);
            }
         }
      }
      statisticsCounter.set(0);
      return statistics;
   }

   protected void interruptStressors() {
      synchronized (this) {
         for (Stressor stressor : stressors) {
            stressor.interrupt();
         }
      }
   }

   public void stopStressors() {
      finished = true;
      steadyState = false;
      interruptStressors();
      List<Stressor> stressors;
      synchronized (this) {
         stressors = new ArrayList<>(this.stressors);
      }
      for (Stressor stressor : stressors) {
         try {
            stressor.join();
         } catch (InterruptedException e) {
            throw new IllegalStateException(e);
         }
      }
      synchronized (this) {
         for (Runnable listener : stopListeners) {
            listener.run();
         }
      }
   }

   public void addStressor(boolean failSilently) {
      int threadIndex = getNextThreadId(maxThreads);
      if (threadIndex < 0) {
         if (failSilently) {
            return;
         }
         reachedMax = true;
         log.warnf("Attempt to create more than %d threads!", maxThreads);
         Utils.threadDump();
         return;
      }
      if (isSteadyState()) {
         if (failSilently) {
            return;
         }
         log.error("Creating new thread during steady-state!");
         Utils.threadDump();
      }
      // mark non-started thread as waiting
      waitingThreads.incrementAndGet();
      Stressor stressor = new Stressor(threadIndex, this, logTransactionExceptions);
      synchronized (this) {
         if (!finished) {
            stressors.add(stressor);
         }
      }
      log.infof("Created stressor %s", stressor.getName());
      stressor.start();
   }

   public int getUsedThreads() {
      return threadCounter.get();
   }

   private int getNextThreadId(int maxThreads) {
      int threadIndex;
      do {
         threadIndex = threadCounter.get();
         if (threadIndex >= maxThreads) {reachedMax = true;
            return -1;
         }
      } while (!threadCounter.compareAndSet(threadIndex, threadIndex + 1));
      return threadIndex;
   }

   public void updateSelector(SchedulingSelector<Conversation> schedulingSelector) {
      this.selector = new ThreadPoolingSelector(schedulingSelector);
   }

   public ConversationSelector getSelector() {
      return selector;
   }

   @Override
   public void beforeServiceStop(boolean graceful) {
      stopStressors();
   }

   public void setMinWaitingThreads(int minWaitingThreads) {
      this.minWaitingThreads = minWaitingThreads;
   }

   public void setMaxThreads(int maxThreads) {
      this.maxThreads = maxThreads;
   }

   public void setLogTransactionExceptions(boolean logTransactionExceptions) {
      this.logTransactionExceptions = logTransactionExceptions;
   }

   public void setMinThreadCreationDelay(long minThreadCreationDelay) {
      this.minThreadCreationDelay = minThreadCreationDelay;
   }

   /**
    * Add operation executed when the test is stopped.
    * @param runnable
    */
   public synchronized void addStopListener(Runnable runnable) {
      stopListeners.add(runnable);
   }

   /**
    * Wraps scheduling selector and monitors number of active threads; when the number of waiting threads drop below
    * actual threshold, spawns another thread.
    *
    * @author Radim Vansa &lt;rvansa@redhat.com&gt;
    */
   class ThreadPoolingSelector implements ConversationSelector {
      private final SchedulingSelector<Conversation> schedulingSelector;

      ThreadPoolingSelector(SchedulingSelector<Conversation> schedulingSelector) {
         this.schedulingSelector = schedulingSelector;
      }

      @Override
      public Conversation next() throws InterruptedException {
         Conversation previous = lastConversation.get();
         if (previous != null) {
            waitingThreads.incrementAndGet();
            actuallyExecuting.get(previous).decrementAndGet();
         }
         Conversation retval = null; // for inspection in finally
         try {
            Conversation conversation = schedulingSelector.next();
            lastConversation.set(conversation);
            AtomicInteger act = actuallyExecuting.get(conversation);
            if (act == null) {
               act = new AtomicInteger();
               AtomicInteger prev = actuallyExecuting.putIfAbsent(conversation, act);
               if (prev != null) act = prev;
            }
            act.incrementAndGet();
            return retval = conversation;
         } finally {
            // when we are interrupted (throwing exception), we're effectively still waiting (not executing conversation)
            // lastConversation is cleared and therefore we won't increment the counter in next round
            if (retval == null) {
               lastConversation.remove();
            } else {
               int currentWaitingThreads = waitingThreads.decrementAndGet();
               if (currentWaitingThreads <= minWaitingThreads && !isFinished()) {
                  long now = TimeService.currentTimeMillis();
                  long timestamp = lastCreated.get();
                  boolean set = false;
                  while (timestamp + minThreadCreationDelay < now) {
                     if (set = lastCreated.compareAndSet(timestamp, now)) {
                        break;
                     }
                     timestamp = lastCreated.get();
                  }
                  if (set) {
                     log.infof("%3d threads waiting", currentWaitingThreads);
                     for (Map.Entry<Conversation, AtomicInteger> entry : actuallyExecuting.entrySet()) {
                        log.infof("%3d threads executing %s", entry.getValue().get(), entry.getKey());
                     }
                     addStressor(false);
                  }
               }
            }
         }
      }
   }
}
