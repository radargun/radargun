package org.radargun.stages.monitor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;
import com.sun.management.HotSpotDiagnosticMXBean;

import org.radargun.sysmonitor.JmxMonitor;
import org.radargun.traits.JmxConnectionProvider;

public class HeapDumpTask extends JmxMonitor implements PeriodicStage.PeriodicTask {

   private HotSpotDiagnosticMXBean heapDumpMXBean;
   private final String dumpDir;
   private final String configName;
   private final int workerIndex;
   private final boolean live;

   public HeapDumpTask(JmxConnectionProvider jmxConnectionProvider, String dumpDir, String configName, int workerIndex, boolean live) {
      super(jmxConnectionProvider, null);
      this.dumpDir = dumpDir;
      this.configName = configName;
      this.workerIndex = workerIndex;
      this.live = live;
   }

   @Override
   public synchronized void start() {
      super.start();
      try {
         heapDumpMXBean = newPlatformMXBeanProxy(connection, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
      } catch (IOException e) {
         throw new IllegalStateException("Cannot create threadMXBean", e);
      }
      File dir = new File(dumpDir);
      if (!dir.exists()) {
         if (!dir.mkdirs()) {
            log.error("Failed to create directory " + dumpDir);
         }
      } else if (!dir.isDirectory()) {
         throw new IllegalStateException("Path specified for heap dumps is not a directory");
      }
   }

   @Override
   public void run() {
      runMonitor();
   }

   @Override
   public void runMonitor() {
      try {
         File heapDumpFile = new File(dumpDir, configName + "." + workerIndex
               + "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".hprof");
         log.info("Dumping heap into " + heapDumpFile.getAbsolutePath());

         heapDumpMXBean.dumpHeap(heapDumpFile.getAbsolutePath(), live);

         log.info("Successfully written heap dump.");
      } catch (Exception e) {
         log.error("Cannot write heap dump!", e);
      }
   }
}
