package org.radargun.service;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class ConfigDumpHelper110 extends ConfigDumpHelper60 {

   protected ObjectName getCacheManagerObjectName(String jmxDomain, String cacheManagerName) throws MalformedObjectNameException {
      String component = "CacheManager";
      return new ObjectName(jmxDomain + ":type=CacheManager,name=" + ObjectName.quote(cacheManagerName) + ",component=" + component);
   }
}
