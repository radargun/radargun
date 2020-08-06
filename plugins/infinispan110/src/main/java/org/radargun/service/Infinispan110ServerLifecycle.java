package org.radargun.service;

import java.util.concurrent.ExecutionException;

import org.radargun.traits.Clustered;

public class Infinispan110ServerLifecycle extends Infinispan100ServerLifecycle {

   public Infinispan110ServerLifecycle(InfinispanServerService service) {
      super(service);
   }

   @Override
   protected void stopInternal() {
      Clustered clustered = this.service.getClustered();
      if (clustered == null) {
         throw new NullPointerException("clustered cannot be null");
      }
      if (clustered instanceof Infinispan100ServerClustered) {
         Infinispan100ServerClustered innerClustered = (Infinispan100ServerClustered) clustered;
         innerClustered.getRestAPI().stopCluster(service.stopTimeout);
         // the stop request return as fast as possible
         long now = System.currentTimeMillis();
         while (System.currentTimeMillis() - now < service.stopTimeout) {
            try {
               Thread.sleep(1000);
               try {
                  innerClustered.getRestAPI().info();
               } catch (ExecutionException e) {
                  // we should kill the inherited process
                  super.stopInternal();
                  break;
               }
            } catch (InterruptedException e) {
               throw new IllegalStateException("InterruptedException", e);
            }
         }
         // if the server still alive, fail
         if (this.service.getLifecycle().isRunning()) {
            throw new IllegalStateException("Error while stopping the server. The server is still running");
         }
      } else {
         throw new IllegalStateException("service.clustered should extends Infinispan100ServerClustered");
      }
   }
}
