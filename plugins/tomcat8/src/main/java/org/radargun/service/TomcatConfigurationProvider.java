package org.radargun.service;

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
public class TomcatConfigurationProvider implements ConfigurationProvider {

   protected final Log log = LogFactory.getLog(getClass());

   private TomcatServerService service;

   public TomcatConfigurationProvider(TomcatServerService service) {
      this.service = service;
   }

   @Override
   public Map<String, Properties> getNormalizedConfigs() {
      return new HashMap<String, Properties>();
   }

   @Override
   public Map<String, byte[]> getOriginalConfigs() {
      Map<String, byte[]> configs = new HashMap<String, byte[]>();
      try {
         Utils.loadConfigFile(service.getFile(), configs);
      } catch (Exception e) {
         log.error("Error while reading original configuration files", e);
      }
      return configs;
   }
}
