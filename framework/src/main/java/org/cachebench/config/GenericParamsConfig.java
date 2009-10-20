package org.cachebench.config;

import java.util.Map;
import java.util.HashMap;

/**
 * Configuration that allows specifying a variable number of key value pairs.
 *
 * @author Mircea.Markus@jboss.com
 */
public class GenericParamsConfig
{
   private Map<String, String> configParams = new HashMap<String, String>();

   public void addParam(NVPair nvPair)
   {
      configParams.put(nvPair.getName(), nvPair.getValue());
   }

   public Map<String, String> getParams()
   {
      return configParams;
   }

   public boolean existsParam(String name)
   {
      return configParams.get(name) != null;
   }

   public String getParamValue(String name)
   {
      return configParams.get(name);
   }

   public int getIntValue(String name)
   {
      return Integer.parseInt(configParams.get(name));
   }

   public boolean getBooleanValue(String paramName)
   {
      return Boolean.valueOf(getParamValue(paramName));
   }
}
