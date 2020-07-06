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
import org.radargun.state.StateBase;
import org.radargun.state.StateListener;
import org.radargun.utils.Utils;

/**
 * Base class for holding and maintaining various worker and main state
 * monitors, extending classes have to implement interface extending
 * {@link StateListener}
 * 
 * @author zhostasa
 *
 * @param <S>
 *           {@link StateBase} implementation to store
 * @param <T>
 *           {@link StateListener} implementation shared with {@link StateBase}
 */
public abstract class AbstractMonitors<S extends StateBase<T>, T extends StateListener> implements StateListener {
   protected final long period;
   protected ScheduledExecutorService exec;
   protected List<Monitor> monitors = new ArrayList<>();
   private static Log log = LogFactory.getLog(AbstractMonitors.class);
   protected S state;

   /**
    * Registers {@link StateBase} and monitor execution period
    * 
    * @param state
    *           {@link StateBase} implementation of the node
    * @param period
    *           Monitor execution period in milliseconds
    */
   public AbstractMonitors(S state, long period) {
      this.state = state;
      this.period = period;
   }

   /**
    * Retrieves {@link ScheduledExecutorService} and registers monitors to
    * execute periodically
    */
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

   /**
    * Stops monitors and {@link ScheduledExecutorService}
    */
   protected synchronized void stopInternal() {
      if (exec == null) return;
      for (Monitor m : monitors) {
         m.stop();
      }
      Utils.shutdownAndWait(exec);
      if (!exec.isTerminated()) {
         log.warn("Failed to terminate monitor executor service.");
      }
      this.exec = null;
   }

   /**
    * Stores monitor (unles already stored) and executes it if monitors are
    * started
    * 
    * @param monitor
    */
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

   /**
    * Adds itself to node state and listeners and starts monitors
    */
   public synchronized void start() {
      state.addListener((T) this);
      state.put(getName(), this);
      startInternal();
   }

   /**
    * Removes itself from node state and listeners and stops monitors
    */
   public synchronized void stop() {
      state.removeListener((T) this);
      state.remove(getName());
      stopInternal();
   }

   public abstract String getName();
}
