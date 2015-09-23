package org.radargun.service;

import org.radargun.Service;
import org.radargun.config.Destroy;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

/**
 * @author Matej Cimbora
 */
@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan80EmbeddedService extends Infinispan70EmbeddedService {

   @Override
   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new Infinispan80EmbeddedQueryable(this);
   }

   @Destroy
   public void destroy() {
      Utils.shutdownAndWait(scheduledExecutor);
   }
}
