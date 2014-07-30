package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class EmbeddedConfigurationProvider60 extends EmbeddedConfigurationProvider {

   public EmbeddedConfigurationProvider60(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   public Map<String, byte[]> getOriginalConfigurations() {
      Map<String, byte[]> configs = new HashMap<String, byte[]>();
      try {
         if (service.dumpConfig) {
            String jgroupsFile = (String) service.cacheManager.getCacheManagerConfiguration().transport().properties().get("configurationFile");
            loadConfigFile(jgroupsFile, configs);
            loadConfigFile(service.configFile, configs);
         }
      } catch (Exception e) {
         log.error("Error while reading configuration file (" + service.configFile + ")", e);
      }
      return configs;
   }
}
