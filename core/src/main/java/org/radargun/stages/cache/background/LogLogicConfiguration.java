package org.radargun.stages.cache.background;

import org.radargun.config.Property;
import org.radargun.utils.TimeConverter;

/**
 * Configuration specific to {@link PrivateLogLogic} or {@link SharedLogLogic}
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class LogLogicConfiguration {
   @Property(doc = "Use values which trace all operation on these keys. Therefore, they're always growing. Default is false.")
   protected boolean enabled = false;

   @Property(doc = "Number of threads on each node that are checking whether all operations from stressor threads have been logged. Default is 10.")
   protected int checkingThreads = 10;

   @Property(doc = "Maximum number of records in one entry before the older ones have to be truncated. Default is 100.")
   protected int valueMaxSize = 100;

   @Property(doc = "Number of operations after which will the stressor or checker update in-cache operation counter. Default is 50.")
   protected long counterUpdatePeriod = 50;

   @Property(doc = "Maximum number of attempts to perform transaction. If the value is negative, number of attempts is unlimited. Default is -1.")
   protected long maxTransactionAttempts = -1;

   @Property(doc = "Maximum number of attempts to perform delayed removes when using transactions (as removes are performed in a separate TX," +
         "which can fail independently of TX performing PUT operations). If the value is negative, number of attempts is unlimited. Default is -1.")
   protected long maxDelayedRemoveAttempts = -1;

   @Property(doc = "Check whether the value that is being removed matches the expected value. In failure scenarios, this may cause " +
         "incorrect test failures. Default is true.")
   protected boolean checkDelayedRemoveExpectedValue = true;

   @Property(doc = "Maximum time for which are the log value checkers allowed to show no new checked values, " +
         "when waiting for checks to complete or stressors to confirm new progress. Default is 10 minutes.",
         converter = TimeConverter.class, deprecatedName = "checkersNoProgressTimeout")
   protected long noProgressTimeout = 600000;

   @Property(doc = "When the log value is full, the stressor needs to wait until all checkers confirm that " +
         "the records have been checked before discarding oldest records. With ignoreDeadCheckers=true " +
         "the stressor does not wait for checkers on dead nodes. Default is false.")
   protected boolean ignoreDeadCheckers = false;

   @Property(doc = "Check that listeners have been fired for each operation on each node (at least once). Default is false.")
   protected boolean checkNotifications = false;

   @Property(doc = "Maximum allowed delay to detect operation confirmed by stressor. Default is no delay.",
         converter = TimeConverter.class)
   protected long writeApplyMaxDelay = 0;

   public boolean isEnabled() {
      return enabled;
   }

   public int getCheckingThreads() {
      return checkingThreads;
   }

   public int getValueMaxSize() {
      return valueMaxSize;
   }

   public long getCounterUpdatePeriod() {
      return counterUpdatePeriod;
   }

   public long getMaxTransactionAttempts() {
      return maxTransactionAttempts;
   }

   public long getMaxDelayedRemoveAttempts() {
      return maxDelayedRemoveAttempts;
   }

   public boolean isCheckDelayedRemoveExpectedValue() {
      return checkDelayedRemoveExpectedValue;
   }

   public long getNoProgressTimeout() {
      return noProgressTimeout;
   }

   public boolean isIgnoreDeadCheckers() {
      return ignoreDeadCheckers;
   }

   public boolean isCheckNotifications() {
      return checkNotifications;
   }

   public long getWriteApplyMaxDelay() {
      return writeApplyMaxDelay;
   }
}
