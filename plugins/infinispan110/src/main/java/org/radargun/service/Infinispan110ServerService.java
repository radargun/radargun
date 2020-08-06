package org.radargun.service;

import org.radargun.Service;

@Service(doc = InfinispanServerService.SERVICE_DESCRIPTION)
public class Infinispan110ServerService extends Infinispan100ServerService {

   protected InfinispanServerLifecycle createServerLifecyle() {
      return new Infinispan110ServerLifecycle(this);
   }
}
