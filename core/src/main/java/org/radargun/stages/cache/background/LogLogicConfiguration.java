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

   @Property(doc = "Maximum time for which are the log value checkers allowed to show no new checked values " +
         "(error is thrown in CheckBackgroundStressors stage). Default is one minute.", converter = TimeConverter.class)
   protected long checkersNoProgressTimeout = 120000;

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

   public long getCheckersNoProgressTimeout() {
      return checkersNoProgressTimeout;
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
