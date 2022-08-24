package org.radargun.stages.monitor;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.ServiceListener;
import org.radargun.utils.TimeConverter;

/**
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class PeriodicStage extends AbstractDistStage {

   protected static final String TASK = PeriodicStage.class.getSimpleName() + "_TASK";

   @Property(doc = "How often should be executed. Default is every 30 minutes.", converter = TimeConverter.class)
   protected long period = 1800000;

   @Property(doc = "Initial delay. Default is 0.", converter = TimeConverter.class)
   protected long initialDelay = 0;

   @Property(doc = "Set this flag to true in order to terminate. Default is false.")
   protected boolean stop = false;

   @Override
   public DistStageAck executeOnWorker() {
      PeriodicTask task = (PeriodicTask) workerState.get(TASK);
      if (task == null) {
         task = createTask();
         workerState.put(TASK, task);
      }
      ScheduledFuture<?> future = (ScheduledFuture<?>) workerState.get(getFutureKey());
      if (stop) {
         task.stop();
         if (future == null) {
            return errorResponse("Scheduled was not found!");
         }
         future.cancel(false);
         workerState.remove(getFutureKey());

         Cleanup cleanup = (Cleanup) workerState.get(getCleanupKey());
         if (cleanup == null) {
            return errorResponse("Cleanup procedure was not found!");
         }
         cleanup.serviceDestroyed();
         workerState.removeListener(cleanup);
         workerState.remove(getCleanupKey());
      } else {
         task.start();

         if (future != null) {
            return errorResponse("Scheduled initialized twice!");
         }

         ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
         future = executor.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS);
         workerState.put(getFutureKey(), future);

         Cleanup cleanup = new Cleanup(executor);
         workerState.addListener(cleanup);
         workerState.put(getCleanupKey(), cleanup);
      }
      return successfulResponse();
   }

   protected static class Cleanup implements ServiceListener{
      private final ScheduledThreadPoolExecutor executor;

      public Cleanup(ScheduledThreadPoolExecutor executor) {
         this.executor = executor;
      }

      @Override
      public void serviceDestroyed() {
         executor.shutdown();
      }
   }
   
   public abstract PeriodicTask createTask();
   
   public abstract String getFutureKey();

   public abstract String getCleanupKey();

   /*
    * This will run inside scheduleAtFixedRate and exceptions won't be propagated
    */
   interface PeriodicTask extends Runnable {

      void start();
      
      void stop();
   }
}
