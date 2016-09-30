package org.radargun.sysmonitor;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

public abstract class AbstractMonitors {
   protected final long period;
   protected ScheduledExecutorService exec;
   protected List<Monitor> monitors = new ArrayList<>();
   private static Log log = LogFactory.getLog(AbstractMonitors.class);

   public AbstractMonitors(long period) {
      this.period = period;
   }

   protected synchronized void startInternal() {
      exec = Executors.newScheduledThreadPool(1, new ThreadFactory() {
         AtomicInteger counter = new AtomicInteger();

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "MonitorThread-" + counter.getAndIncrement());
         }
      });
      for (Monitor m : monitors) {
         m.start();
         exec.scheduleAtFixedRate(m, 0, period, TimeUnit.MILLISECONDS);
      }
      log.infof("Gathering statistics every %d ms", period);
   }

   protected synchronized void stopInternal() {
      if (exec == null) return;
      for (Monitor m : monitors) {
         m.stop();
      }
      exec.shutdownNow();
      try {
         exec.awaitTermination(2 * period, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         log.error("Failed to terminate local monitoring.");
      }
      this.exec = null;
   }

   public synchronized void addMonitor(Monitor monitor) {
      if (monitors.contains(monitor)) {
         log.warnf("Monitor %s is already registered, ignoring", monitor);
         return;
      }
      monitors.add(monitor);
      if (exec != null) {
         exec.scheduleAtFixedRate(monitor, 0, period, TimeUnit.MILLISECONDS);
      }
   }
}
