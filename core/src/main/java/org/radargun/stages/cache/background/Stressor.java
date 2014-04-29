package org.radargun.stages.cache.background;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.stats.DefaultOperationStats;
import org.radargun.stats.SynchronizedStatistics;

/**
* Stressor thread running during many stages.
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
class Stressor extends Thread {

   private static final Log log = LogFactory.getLog(Stressor.class);
   private static final boolean trace = log.isTraceEnabled();

   protected final int id;
   private final Logic logic;
   private final long delayBetweenRequests;
   private final boolean loadOnly;
   protected final SynchronizedStatistics stats = new SynchronizedStatistics(new DefaultOperationStats());

   private volatile boolean terminate = false;
   private boolean loaded;

   public Stressor(BackgroundOpsManager manager, Logic logic, int id) {
      super("StressorThread-" + id);
      this.id = manager.getSlaveIndex() * manager.getNumThreads() + id;
      this.logic = logic;
      this.delayBetweenRequests = manager.getDelayBetweenRequests();
      this.loadOnly = manager.getLoadOnly();
      logic.setStressor(this);
      stats.begin();
   }

   @Override
   public void run() {
      try {
         if (!loaded) {
            logic.loadData();
            loaded = true;
         }
         if (loadOnly) {
            log.info("The stressor has finished loading data and will terminate.");
            return;
         }
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

   public boolean isLoaded() {
      return loaded;
   }

   public void setLoaded(boolean loaded) {
      this.loaded = loaded;
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
}
