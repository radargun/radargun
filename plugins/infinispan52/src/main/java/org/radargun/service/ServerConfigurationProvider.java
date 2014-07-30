package org.radargun.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class ServerConfigurationProvider extends AbstractConfigurationProvider {

   private InfinispanServerService service;

   public ServerConfigurationProvider(InfinispanServerService service) {
      this.service = service;
   }

   @Override
   public Map<String, Properties> getNormalizedConfigurations() {
      return new HashMap<String, Properties>();
   }

   @Override
   public Map<String, byte[]> getOriginalConfigurations() {
      Map<String, byte[]> configs = new HashMap<String, byte[]>();
      if (service.dumpConfig) {
         loadConfigFile(service.file, configs);
      }
      return configs;
   }
}
