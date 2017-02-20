package org.radargun.state;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface ServiceListener extends StateListener {

   /**
    * Called before Lifecycle.start() is called on service's trait.
    */
   default void beforeServiceStart() {};

   /**
    * Called after Lifecycle.start() is called on service's trait
    */
   default void afterServiceStart() {};

   /**
    * Called before Lifecycle.stop() (graceful=true) or Killable.kill*() (graceful=false) is called.
    */
   default void beforeServiceStop(boolean graceful) {};

   /**
    * Called after Lifecycle.stop() (graceful=true) or Killable.kill*() (graceful=false) is called.
    */
   default void afterServiceStop(boolean graceful) {};

   /**
    * Called on the end of benchmark when the service won't be used anymore. Useful for doing cleanup.
    */
   default void serviceDestroyed() {};
}
