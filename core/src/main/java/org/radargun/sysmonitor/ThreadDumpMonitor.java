package org.radargun.sysmonitor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.radargun.reporting.Timeline;
import org.radargun.traits.JmxConnectionProvider;

import static java.lang.management.ManagementFactory.THREAD_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;

public class ThreadDumpMonitor extends JmxMonitor {

   private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss");

   private ThreadMXBean threadMXBean;
   private final boolean lockedMonitors;
   private final boolean lockedSynchronizers;
   private final int workerIndex;


   public ThreadDumpMonitor(JmxConnectionProvider jmxConnectionProvider, Timeline timeline,
                            boolean dumpThreadLockedMonitors, boolean dumpThreadLockedSynchronizers, int workerIndex) {
      super(jmxConnectionProvider, timeline);
      this.lockedMonitors = dumpThreadLockedMonitors;
      this.lockedSynchronizers = dumpThreadLockedSynchronizers;
      this.workerIndex = workerIndex;
   }

   public synchronized void runMonitor() {
      try {
         if (connection == null) {
            log.warn("MBean connection is not open, cannot read Thread Dump");
            return;
         }
         threadDump();
      } catch (Exception e) {
         log.error("Exception!", e);
      }
   }

   @Override
   public synchronized void start() {
      super.start();
      try {
         this.threadMXBean = newPlatformMXBeanProxy(connection, THREAD_MXBEAN_NAME, ThreadMXBean.class);
      } catch (IOException e) {
         throw new IllegalStateException("Cannot create threadMXBean", e);
      }
   }

   @Override
   public synchronized void stop() {
      super.stop();
   }

   public void threadDump() {
      log.info("Generating thread dump for: " + this.workerIndex);
      String fileName = String.format("thread-dump-worker-%s-%s.dump", this.workerIndex, DATE_FORMATTER.format(LocalDateTime.now()));
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))){
         for(ThreadInfo threadInfo : threadMXBean.dumpAllThreads(this.lockedMonitors, this.lockedSynchronizers)) {
            writer.append(threadInfo.toString());
         }
      } catch (IOException e) {
         log.error("Error while generating thread dump", e);
      }
      log.info("Thread dump generated for: " + this.workerIndex);
   }
}
