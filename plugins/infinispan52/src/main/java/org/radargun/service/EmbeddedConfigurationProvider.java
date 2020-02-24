package org.radargun.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.infinispan.Cache;
import org.infinispan.configuration.global.GlobalConfiguration;

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
         GlobalConfiguration global = service.cacheManager.getCacheManagerConfiguration();
         String jmxDomain = getJmxDomain(global);
         Properties globalProperties = configDumpHelper.dumpGlobal(global, jmxDomain, service.cacheManager.getName());
         if (!globalProperties.isEmpty()) {
            configurationMap.put("global", globalProperties);
         }
         for (Map.Entry<String, Cache> cache : service.caches.entrySet()) {
            Properties cacheProperties = configDumpHelper.dumpCache(
               cache.getValue().getAdvancedCache().getCacheConfiguration(), jmxDomain,
               service.cacheManager.getName(), cache.getValue().getName());
            if (cacheProperties != null && !cacheProperties.isEmpty()) {
               configurationMap.put("cache_" + cache.getValue().getName(), cacheProperties);
            }
         }
         String clusterName = global.transport() == null ? "default" : global.transport().clusterName();
         Properties jgroupsProperties = configDumpHelper.dumpJGroups(jmxDomain, clusterName);
         if (!jgroupsProperties.isEmpty()) {
            configurationMap.put("jgroups", jgroupsProperties);
         }
      } catch (Exception e) {
         log.error("Error while creating normalized configuration files", e);
      }
      return configurationMap;
   }

   protected String getJmxDomain(GlobalConfiguration global) {
      return global.globalJmxStatistics().domain();
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
