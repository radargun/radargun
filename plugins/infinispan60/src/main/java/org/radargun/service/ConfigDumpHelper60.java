package org.radargun.service;

import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.global.GlobalConfiguration;

/**
 * ConfigDumpHelper that uses Infinispan 6.0's own property dumping mechanism.
 *
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
public class ConfigDumpHelper60 extends ConfigDumpHelper {

   private ObjectName findCacheBean(MBeanServer mbeanServer, String domain, String managerName, String cacheName) throws MalformedObjectNameException {
      Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName(
         String.format("%s:type=Cache,name=*,manager=\"%s\",component=Cache", domain, managerName)), null);
      for (ObjectInstance beanObj : beanObjs) {
         String name = beanObj.getObjectName().getKeyProperty("name");
         if (name != null && (name.startsWith(cacheName) || name.startsWith("\"" + cacheName))) {
            return beanObj.getObjectName();
         }
      }
      return null;
   }

   @Override
   public Properties dumpCache(Configuration configuration, String jmxDomain, String managerName, String cacheName) {
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         ObjectName cacheObj = findCacheBean(mbeanServer, jmxDomain, managerName, cacheName);
         if (cacheObj == null) {
            log.error("No cache named " + cacheName + " found among MBeans");
            return null;
         }
         return (Properties) mbeanServer.getAttribute(cacheObj, "configurationAsProperties");
      } catch (Exception e) {
         log.error("Error while dumping " + cacheName + " cache config as properties", e);
         return null;
      }
   }

   @Override
   public Properties dumpGlobal(GlobalConfiguration globalConfiguration, String jmxDomain, String managerName) {
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         ObjectName objCacheManager = new ObjectName(String.format("%s:type=CacheManager,name=\"%s\",component=CacheManager", jmxDomain, managerName));
         return (Properties) mbeanServer.getAttribute(objCacheManager, "globalConfigurationAsProperties");
      } catch (Exception e) {
         log.error("Error while dumping global config as properties", e);
         return null;
      }
   }
}
