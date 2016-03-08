package org.radargun.stages.cache.background;

/**
 * The class with business logic of the background stressor.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
interface Logic {
   /**
    * Initialize the logic within thread. Called before first invoke but with all the caches set.
    */
   void init();

   /**
    * Execute one (logical) operation on the cache.
    *
    * @throws InterruptedException
    */
   void invoke() throws InterruptedException;

   /**
    * Stop all operations and flush. Used e.g. for last transaction rollback.
    */
   void finish();

   /**
    * Setup the stressor that will call logic()
    *
    * @param stressor
    */
   void setStressor(Stressor stressor);

   /**
    * @return Arbitrary string describing status of the logic.
    */
   String getStatus();
}
