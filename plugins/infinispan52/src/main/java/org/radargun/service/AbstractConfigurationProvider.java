package org.radargun.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
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
         loadConfigFile(getJGroupsConfigFile(), configs);
         loadConfigFile(getConfigFile(), configs);
      } catch (Exception e) {
         log.error("Error while reading original configuration files", e);
      }
      return configs;
   }

   private void loadConfigFile(String filename, Map<String, byte[]> configs) {
      if (filename != null) {
         try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(filename);
            if(is == null) { //not attached as resource - assume the direct path on filesystem
               is = new FileInputStream(new File(filename));
            }
            configs.put(filename, Utils.readAsBytes(is));
         } catch (Exception e) {
            log.error("Error while reading configuration file (" + filename + ")", e);
         }
      }
   }
}
