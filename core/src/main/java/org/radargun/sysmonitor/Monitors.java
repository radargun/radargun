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
import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;
import org.radargun.traits.Lifecycle;

/**
 * Retrieves JMX statistics from the local machine.
 */
public class Monitors implements ServiceListener {
   public static final String MONITORS = Monitors.class.getName();
   private static Log log = LogFactory.getLog(Monitors.class);

   private int frequency = 1;
   private TimeUnit timeUnit = TimeUnit.SECONDS;

   private final SlaveState slaveState;
   private ScheduledExecutorService exec;
   private List<Monitor> monitors = new ArrayList<>();

   public Monitors(SlaveState slaveState, int frequency, TimeUnit timeUnit) {
      this.slaveState = slaveState;
      this.frequency = frequency;
      this.timeUnit = timeUnit;
   }

   public synchronized void start() {
      slaveState.put(MONITORS, this);
      slaveState.addServiceListener(this);
      Lifecycle lifecycle = slaveState.getTrait(Lifecycle.class);
      if (lifecycle != null && lifecycle.isRunning()) {
         startInternal();
      }
   }

   private synchronized void startInternal() {
      exec = Executors.newScheduledThreadPool(1, new ThreadFactory() {
         AtomicInteger counter = new AtomicInteger();
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "MonitorThread-" + counter.getAndIncrement());
         }
      });
      for (Monitor m : monitors) {
         m.start();
         exec.scheduleAtFixedRate(m, 0, frequency, timeUnit);
      }
      log.infof("Gathering statistics every %d %s", frequency, timeUnit.name());
   }

   public synchronized void addMonitor(Monitor monitor) {
      if (monitors.contains(monitor)) {
         log.warnf("Monitor %s is already registered, ignoring", monitor);
         return;
      }
      monitors.add(monitor);
      if (exec != null) {
         exec.scheduleAtFixedRate(monitor, 0, frequency, timeUnit);
      }
   }

   public synchronized void stop() {
      slaveState.removeServiceListener(this);
      slaveState.remove(MONITORS);
      stopInternal();
   }
   
   private synchronized void stopInternal() {
      if (exec == null) return;
      for (Monitor m : monitors) {
         m.stop();
      }
      exec.shutdownNow();
      try {
         exec.awaitTermination(2 * frequency, timeUnit);
      } catch (InterruptedException e) {
         log.error("Failed to terminate local monitoring.");
      }
      this.exec = null;
   }

   @Override
   public void beforeServiceStart() {
   }

   @Override
   public void afterServiceStart() {
      startInternal();
   }

   @Override
   public void beforeServiceStop(boolean graceful) {
      stopInternal();
   }

   @Override
   public void afterServiceStop(boolean graceful) {
   }

   @Override
   public void serviceDestroyed() {
      stop();
   }
}
