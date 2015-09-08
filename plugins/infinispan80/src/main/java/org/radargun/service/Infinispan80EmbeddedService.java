package org.radargun.service;

import org.radargun.Service;
import org.radargun.config.Destroy;
import org.radargun.utils.Utils;

import java.util.concurrent.ForkJoinPool;

/**
 * @author Matej Cimbora
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan80EmbeddedService extends Infinispan70EmbeddedService {

   @Destroy
   public void destroy() {
      Utils.shutdownAndWait(scheduledExecutor);
      ForkJoinPool.commonPool().shutdownNow();
   }
}
