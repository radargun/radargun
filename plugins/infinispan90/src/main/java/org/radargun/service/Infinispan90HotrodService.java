package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Matej Cimbora
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan90HotrodService extends Infinispan81HotrodService {

   @ProvidesTrait
   public ContinuousQuery createContinuousQuery() {
      return new Infinispan90HotrodContinuousQuery(this);
   }

}
