package org.radargun.service;

import org.radargun.Service;
import org.radargun.config.Destroy;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

import java.util.concurrent.ForkJoinPool;

/**
 * @author Matej Cimbora
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan80EmbeddedService extends Infinispan70EmbeddedService {

   @ProvidesTrait
   public Infinispan80EmbeddedContinuousQuery createContinuousQuery() {
      return new Infinispan80EmbeddedContinuousQuery(this);
   }

   @Destroy
   public void destroy() {
      Utils.shutdownAndWait(scheduledExecutor);
      ForkJoinPool.commonPool().shutdownNow();
   }
}
