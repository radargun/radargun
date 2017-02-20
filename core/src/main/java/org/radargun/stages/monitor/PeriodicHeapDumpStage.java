package org.radargun.stages.monitor;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.ServiceListener;
import org.radargun.utils.TimeConverter;
import org.radargun.utils.Utils;

/**
 * // TODO: Allow heap dumps of remote (server) process, too.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Periodically generates heap dumps.")
public class PeriodicHeapDumpStage extends AbstractDistStage {
   protected static final String CLEANUP = PeriodicHeapDumpStage.class.getSimpleName() + "_CLEANUP";
   protected static final String FUTURE = PeriodicHeapDumpStage.class.getSimpleName() + "_FUTURE";

   @Property(doc = "Location on disk where the heap dumps should be stored.", optional = false)
   protected String dir;

   @Property(doc = "How often should be the heap dumps created. Default is every 30 minutes.", converter = TimeConverter.class)
   protected long period = 1800000;

   @Property(doc = "Delay before the first heap dump. Default is 0.", converter = TimeConverter.class)
   protected long initialDelay = 0;

   @Property(doc = "Set this flag to true in order to terminate the heap dumper. Default is false.")
   protected boolean stop = false;

   @Override
   public DistStageAck executeOnSlave() {
      ScheduledFuture<?> future = (ScheduledFuture<?>) slaveState.get(FUTURE);
      if (stop) {
         if (future == null) {
            return errorResponse("Heap dumps have not been scheduled!");
         }
         future.cancel(false);
         slaveState.remove(FUTURE);

         Cleanup cleanup = (Cleanup) slaveState.get(CLEANUP);
         if (cleanup == null) {
            return errorResponse("Cleanup procedure was not found!");
         }
         cleanup.serviceDestroyed();
         slaveState.removeListener(cleanup);
         slaveState.remove(CLEANUP);
      } else {
         if (future != null) {
            return errorResponse("Periodic heap dumps are already running!");
         }
         File dir = new File(this.dir);
         if (!dir.exists()) {
            if (!dir.mkdirs()) {
               log.error("Failed to create directory " + this.dir);
            }
         } else if (!dir.isDirectory()) {
            return errorResponse("Path specified for heap dumps is not a directory");
         }

         ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
         future = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
               try {
                  File heapDumpFile = new File(PeriodicHeapDumpStage.this.dir, slaveState.getConfigName() + "." + slaveState.getSlaveIndex()
                     + "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".hprof");
                  log.info("Dumping heap into " + heapDumpFile.getAbsolutePath());
                  Utils.dumpHeap(heapDumpFile.getAbsolutePath());
                  log.info("Successfully written heap dump.");
               } catch (Exception e) {
                  log.error("Cannot write heap dump!", e);
               }

            }
         }, initialDelay, period, TimeUnit.MILLISECONDS);
         slaveState.put(FUTURE, future);

         Cleanup cleanup = new Cleanup(executor);
         slaveState.addListener(cleanup);
         slaveState.put(CLEANUP, cleanup);
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
}
