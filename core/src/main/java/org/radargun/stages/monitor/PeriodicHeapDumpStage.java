package org.radargun.stages.monitor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;
import com.sun.management.HotSpotDiagnosticMXBean;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.ServiceListener;
import org.radargun.sysmonitor.JmxMonitor;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.utils.TimeConverter;

/**
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

   @Property(doc = "If set it only prints objects which have active references and discards the ones that are ready to be garbage collected")
   protected boolean live = false;

   @InjectTrait
   private JmxConnectionProvider jmxConnectionProvider;

   @Override
   public DistStageAck executeOnWorker() {
      HeapDumpTask heapDumpTask = new HeapDumpTask(jmxConnectionProvider);
      ScheduledFuture<?> future = (ScheduledFuture<?>) workerState.get(FUTURE);
      if (stop) {
         heapDumpTask.stop();
         if (future == null) {
            return errorResponse("Heap dumps have not been scheduled!");
         }
         future.cancel(false);
         workerState.remove(FUTURE);

         Cleanup cleanup = (Cleanup) workerState.get(CLEANUP);
         if (cleanup == null) {
            return errorResponse("Cleanup procedure was not found!");
         }
         cleanup.serviceDestroyed();
         workerState.removeListener(cleanup);
         workerState.remove(CLEANUP);
      } else {
         heapDumpTask.start();
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
         future = executor.scheduleAtFixedRate(heapDumpTask, initialDelay, period, TimeUnit.MILLISECONDS);
         workerState.put(FUTURE, future);

         Cleanup cleanup = new Cleanup(executor);
         workerState.addListener(cleanup);
         workerState.put(CLEANUP, cleanup);
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

   private class HeapDumpTask extends JmxMonitor implements Runnable {

      private HotSpotDiagnosticMXBean heapDumpMXBean;

      public HeapDumpTask(JmxConnectionProvider jmxConnectionProvider) {
         super(jmxConnectionProvider, null);
      }

      @Override
      public synchronized void start() {
         super.start();
         try {
            heapDumpMXBean = newPlatformMXBeanProxy(connection, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
         } catch (IOException e) {
            throw new IllegalStateException("Cannot create threadMXBean", e);
         }
      }

      @Override
      public void run() {
         runMonitor();
      }

      @Override
      public void runMonitor() {
         try {
            File heapDumpFile = new File(dir, workerState.getConfigName() + "." + workerState.getWorkerIndex()
                  + "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".hprof");
            log.info("Dumping heap into " + heapDumpFile.getAbsolutePath());

            heapDumpMXBean.dumpHeap(heapDumpFile.getAbsolutePath(), live);

            log.info("Successfully written heap dump.");
         } catch (Exception e) {
            log.error("Cannot write heap dump!", e);
         }
      }
   }
}
