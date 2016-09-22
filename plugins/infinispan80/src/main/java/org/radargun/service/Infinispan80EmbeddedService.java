package org.radargun.service;

import java.util.concurrent.ForkJoinPool;

import org.radargun.Service;
import org.radargun.config.Destroy;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

/**
 * @author Matej Cimbora
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan80EmbeddedService extends Infinispan70EmbeddedService {

   @ProvidesTrait
   public ContinuousQuery createContinuousQuery() {
      return new Infinispan80EmbeddedContinuousQuery(this);
   }

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
