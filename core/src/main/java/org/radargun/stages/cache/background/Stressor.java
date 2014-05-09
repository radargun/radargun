package org.radargun.stages.cache.background;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.DefaultOperationStats;
import org.radargun.stats.SynchronizedStatistics;

/**
* Stressor thread running in parallel to many stages.
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
class Stressor extends Thread {

   private static final Log log = LogFactory.getLog(Stressor.class);
   private static final boolean trace = log.isTraceEnabled();

   protected final int id;
   private final Logic logic;
   private final long delayBetweenRequests;
   protected final SynchronizedStatistics stats = new SynchronizedStatistics(new DefaultOperationStats());

   private volatile boolean terminate = false;

   public Stressor(BackgroundOpsManager manager, Logic logic, int id) {
      super("StressorThread-" + id);
      GeneralConfiguration config = manager.getGeneralConfiguration();
      this.id = manager.getSlaveIndex() * config.getNumThreads() + id;
      this.logic = logic;
      this.delayBetweenRequests = config.getDelayBetweenRequests();
      logic.setStressor(this);
      stats.begin();
   }

   @Override
   public void run() {
      try {
         while (!isInterrupted() && !terminate) {
            logic.invoke();
            if (delayBetweenRequests > 0)
               sleep(delayBetweenRequests);
         }
      } catch (InterruptedException e) {
         log.trace("Stressor interrupted.", e);
         logic.finish();
      }
   }

   public void requestTerminate() {
      terminate = true;
   }

   public boolean isTerminated() {
      return terminate;
   }

   public SynchronizedStatistics getStatsSnapshot(boolean reset) {
      SynchronizedStatistics snapshot = stats.snapshot(reset);
      return snapshot;
   }

   public String getStatus() {
      return String.format("%s [id=%d, terminated=%s]: %s [%s]", getName(), id, terminate,
            logic.getClass().getSimpleName(), logic.getStatus());
   }

   public Logic getLogic() {
      return logic;
   }
}
