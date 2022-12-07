package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.client.hotrod.impl.transport.netty.ChannelFactory;
import org.radargun.Service;

/**
 * @author Diego Lovison
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan92HotrodService extends Infinispan90HotrodService {

   @Override
   public Map<String, Number> getValues() {
      Map<String, Number> values = new HashMap<>();

      ChannelFactory nrFactory = managerNoReturn.getChannelFactory();
      ChannelFactory frFactory = managerForceReturn.getChannelFactory();
      if (nrFactory != null) {
         values.put("NR ConnectionPool Active", nrFactory.getNumActive());
         values.put("NR ConnectionPool Idle", nrFactory.getNumIdle());
      }
      if (frFactory != null) {
         values.put("FR ConnectionPool Active", frFactory.getNumActive());
         values.put("FR ConnectionPool Idle", frFactory.getNumIdle());
      }
      return values;
   }
}