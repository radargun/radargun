package org.radargun.sysmonitor;

/**
 * The monitoring task, that should be {@link #start() started}, {@link #stop() stopped}
 * and invoked through {@link #run()}.
 */
public interface Monitor extends Runnable {
   void start();

   void stop();
}
