package org.radargun.state;

/**
 * Listener called on master node
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface MasterListener {
   default void beforeConfiguration() {
   };

   default void afterConfiguration() {
   };

   default void beforeCluster() {
   };

   default void afterCluster() {
   };

}
