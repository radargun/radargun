package org.radargun.state;

public interface ServiceListener {

   /**
    * Called before Lifecycle.start() is called on service's trait.
    */
   void beforeServiceStart();

   /**
    * Called after Lifecycle.start() is called on service's trait
    */
   void afterServiceStart();

   /**
    * Called before Lifecycle.stop() (graceful=true) or Killable.kill*() (graceful=false) is called.
    */
   void beforeServiceStop(boolean graceful);

   /**
    * Called after Lifecycle.stop() (graceful=true) or Killable.kill*() (graceful=false) is called.
    */
   void afterServiceStop(boolean graceful);

   /**
    * Called on the end of benchmark when the service won't be used anymore. Useful for doing cleanup.
    */
   void serviceDestroyed();
}
