package org.radargun.traits;

import java.util.Map;
import java.util.Properties;

/**
 *  @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
@Trait(doc = "Allows to access service configurations")
public interface ConfigurationProvider {
   /**
    * Gets service's normalized configuration files in form of property files. Keys represent configuration names.
    *
    * @return normalized config properties
    */
   Map<String, Properties> getNormalizedConfigs();

   /**
    * Gets service's original configuration files in form of byte arrays. Keys represent file names (including extension).
    *
    * @return original config files
    */
   Map<String, byte[]> getOriginalConfigs();
}