package org.radargun.stages.cache.background;

/**
 *
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
interface Logic {
   void loadData();
   void invoke() throws InterruptedException;
   void finish();
   void setStressor(Stressor stressor);
   String getStatus();
}
