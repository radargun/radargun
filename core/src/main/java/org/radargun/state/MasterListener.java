package org.radargun.state;

/**
 * Listener called on master node
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface MasterListener extends StateListener {

   /**
    * Fired before each configuration is executed on clusters
    */
   default void beforeConfiguration() {
   };

   /**
    * Fired before scenario run on each cluster
    */
   default void beforeCluster() {
   };

   /**
    * Fired after scenario run on each cluster
    */
   default void afterCluster() {
   };

   /**
    * Fired after whole configuration is executed for all clusters
    */
   default void afterConfiguration() {
   };

}
