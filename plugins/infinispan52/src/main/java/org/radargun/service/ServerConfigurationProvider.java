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
   public Map<String, Properties> getNormalizedConfigs() {
      return new HashMap<String, Properties>();
   }

   @Override
   public String getConfigFile() {
      return service.file;
   }

   @Override
   public String getJGroupsConfigFile() {
      return null;
   }
}
