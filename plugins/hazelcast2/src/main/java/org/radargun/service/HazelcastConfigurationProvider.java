package org.radargun.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.ConfigurationProvider;
import org.radargun.utils.Utils;

/**
 * @author Martin Gencur
 */
public class HazelcastConfigurationProvider implements ConfigurationProvider {

   protected final Log log = LogFactory.getLog(getClass());

   protected HazelcastService service;

   public HazelcastConfigurationProvider(HazelcastService service) {
      this.service = service;
   }

   @Override
   public Map<String, Properties> getNormalizedConfigs() {
      return Collections.EMPTY_MAP; //not implemented
   }

   @Override
   public Map<String, byte[]> getOriginalConfigs() {
      Map<String, byte[]> configs = new HashMap<>();
      try {
         Utils.loadConfigFile(service.config, configs);
      } catch (Exception e) {
         log.error("Error while reading original configuration file!", e);
      }
      return configs;
   }
}
