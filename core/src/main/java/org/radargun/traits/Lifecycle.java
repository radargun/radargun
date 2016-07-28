package org.radargun.traits;

/**
 * Without this trait the service is considered always running.
 */
@Trait(doc = "Provides basic access to lifecycle of the service.")
public interface Lifecycle {
   /**
    * Start the service.
    */
   void start();

   /**
    * Graciously shutdown the service.
    */
   void stop();

   /**
    * @return True if the service was started but not stopped.
    */
   boolean isRunning();
}
