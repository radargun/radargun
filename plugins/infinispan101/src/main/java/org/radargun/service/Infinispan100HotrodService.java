package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.client.hotrod.configuration.ConnectionPoolConfigurationBuilder;
import org.radargun.Service;

@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan100HotrodService extends Infinispan93HotrodService {

   @Override
   public Map<String, Number> getValues() {
      Map<String, Number> values = new HashMap<>();
      values.put("NR ConnectionPool Active", managerNoReturn.getActiveConnectionCount());
      values.put("NR ConnectionPool Idle", managerNoReturn.getIdleConnectionCount());
      values.put("FR ConnectionPool Active", managerForceReturn.getActiveConnectionCount());
      values.put("FR ConnectionPool Idle", managerForceReturn.getIdleConnectionCount());
      return values;
   }

   /*
    * We are not calling super here because ConnectionPoolConfigurationBuilder.maxTotal(int) was removed in ISPN10
    */
   @Override
   protected void configureConnectionPool(ConnectionPoolConfigurationBuilder poolConfigurationBuilder) {
      poolConfigurationBuilder.maxActive(maxConnectionsServer);
   }
}
