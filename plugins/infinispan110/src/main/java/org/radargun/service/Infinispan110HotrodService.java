package org.radargun.service;

import org.radargun.Service;

@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan110HotrodService extends Infinispan100HotrodService {

   @Override
   protected Infinispan110HotrodQueryable createQueryable() {
      return new Infinispan110HotrodQueryable(this);
   }
}
