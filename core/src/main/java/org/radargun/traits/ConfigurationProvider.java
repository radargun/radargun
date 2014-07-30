package org.radargun.traits;

import java.util.Map;
import java.util.Properties;

/**
 *  @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
@Trait(doc = "Allows to access slave configurations")
public interface ConfigurationProvider {
   /**
    * Gets slave's normalized configuration files in form of property files. Keys represent configuration names.
    *
    * @return normalized config properties
    */
   Map<String, Properties> getNormalizedConfigurations();
   /**
    * Gets slave's original configuration files in form of byte arrays. Keys represent file names (including extension).
    *
    * @return original config files
    */
   Map<String, byte[]> getOriginalConfigurations();
}