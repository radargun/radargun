package org.radargun.service;

import java.util.concurrent.ForkJoinPool;

import org.radargun.config.Destroy;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

/**
 * @author Matej Cimbora
 */
public abstract class Infinispan80EmbeddedService extends Infinispan70EmbeddedService {

   public abstract ContinuousQuery createContinuousQuery();

   @ProvidesTrait
   public Infinispan80Streamable createStreamable() {
      return new Infinispan80Streamable(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new Infinispan80EmbeddedQueryable(this);
   }


   @Destroy
   public void destroy() {
      Utils.shutdownAndWait(scheduledExecutor);
      ForkJoinPool.commonPool().shutdownNow();
   }
}
