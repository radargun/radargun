package org.radargun.sysmonitor;

/**
 * 
 * AbstractMonitor is the base class for all system monitors. It manages the lifecycle of the
 * monitor, so that no values are written to the reports before the benchmark starts or ends.
 * 
 */
public abstract class AbstractMonitor implements Monitor {
   protected volatile boolean shouldRun = false;

   @Override
   public void run() {
      if (shouldRun) {
         runMonitor();
      }
   }

   @Override
   public void start() {
      shouldRun = true;
   }

   @Override
   public void stop() {
      shouldRun = false;
   }

   public abstract void runMonitor();

}
