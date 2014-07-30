package org.radargun.service;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class EmbeddedConfigurationProvider60 extends EmbeddedConfigurationProvider {

   public EmbeddedConfigurationProvider60(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   public String getJGroupsConfigFile() {
      return service.cacheManager.getCacheManagerConfiguration().transport().properties().getProperty("configurationFile");
   }
}
