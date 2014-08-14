package org.radargun.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.infinispan.Cache;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class EmbeddedConfigurationProvider extends AbstractConfigurationProvider {

   protected Infinispan52EmbeddedService service;

   public EmbeddedConfigurationProvider(Infinispan52EmbeddedService service) {
      this.service = service;
   }

   @Override
   public Map<String, Properties> getNormalizedConfigs() {
      Map<String, Properties> configurationMap = new HashMap<String, Properties>(3);
      try {
         ConfigDumpHelper configDumpHelper = service.createConfigDumpHelper();
         configurationMap.put("global", configDumpHelper.dumpGlobal(
               service.cacheManager.getCacheManagerConfiguration(), service.cacheManager.getName()));
         for (Map.Entry<String,Cache> cache : service.caches.entrySet()) {
            configurationMap.put("cache_" + cache.getValue().getName(),configDumpHelper.dumpCache(cache.getValue().getAdvancedCache().getCacheConfiguration(),
                  service.cacheManager.getName(), cache.getValue().getName()));
         }
         configurationMap.put("jgroups", configDumpHelper.dumpJGroups(service.cacheManager.getCacheManagerConfiguration().transport() == null
               ? null : service.cacheManager.getCacheManagerConfiguration().transport().clusterName()));
      } catch (Exception e) {
         log.error("Error while creating normalized configuration files", e);
      }
      return configurationMap;
   }

   @Override
   public String getConfigFile() {
      return service.configFile;
   }

   @Override
   public String getJGroupsConfigFile() {
      return service.cacheManager.getCacheManagerConfiguration().transport().properties().getProperty("configurationFile");
   }
}
