package org.radargun.state;

/**
 * Listener called on master node
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface MasterListener {
   void beforeConfiguration();
   void afterConfiguration();
   void beforeCluster();
   void afterCluster();

   class Adapter implements MasterListener {
      @Override
      public void beforeConfiguration() {}

      @Override
      public void afterConfiguration() {}

      @Override
      public void beforeCluster() {}

      @Override
      public void afterCluster() {}
   }
}
