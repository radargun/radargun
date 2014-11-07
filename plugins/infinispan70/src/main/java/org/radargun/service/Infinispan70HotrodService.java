package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Vojtech Juranek &lt;vjuranek@redhat.com&gt;
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan70HotrodService extends Infinispan60HotrodService {

   public RemoteCacheManager getRemoteManager(boolean forceReturn) {
      return forceReturn ? managerForceReturn : managerNoReturn;
   }

   @ProvidesTrait
   public InfinispanClientListeners createListeners() {
      return new InfinispanClientListeners(this);
   }

}
