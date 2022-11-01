package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ConfigurationProvider;
import org.radargun.utils.Utils;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public abstract class AbstractConfigurationProvider implements ConfigurationProvider {

   protected final Log log = LogFactory.getLog(getClass());

   protected abstract String getConfigFile();

   protected abstract String getJGroupsConfigFile();

   @Override
   public Map<String, byte[]> getOriginalConfigs() {
      Map<String, byte[]> configs = new HashMap<String, byte[]>();
      try {
         Utils.loadConfigFile(getJGroupsConfigFile(), configs);
         Utils.loadConfigFile(getConfigFile(), configs);
      } catch (Exception e) {
         log.error("Error while reading original configuration files", e);
      }
      return configs;
   }
}
