package org.radargun.service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.radargun.utils.Utils;

/**
 * ConfigDumpHelper that uses Infinispan 6.0's own property dumping mechanism.
 * 
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 * 
 */
public class ConfigDumpHelper60 extends ConfigDumpHelper {

   private ObjectName findCacheBean(MBeanServer mbeanServer, String managerName, String cacheName) throws MalformedObjectNameException {
      Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName("jboss.infinispan:type=Cache,name=*,manager=\"" + managerName
            + "\",component=Cache"), null);
      for (ObjectInstance beanObj : beanObjs) {
         String name = beanObj.getObjectName().getKeyProperty("name");
         if (name != null && (name.startsWith(cacheName) || name.startsWith("\"" + cacheName))) {
            return beanObj.getObjectName();
         }
      }
      return null;
   }

   @Override
   public boolean dumpCache(File dumpFile, Configuration configuration, String managerName, String cacheName) {
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         ObjectName cacheObj = findCacheBean(mbeanServer, managerName, cacheName);
         if (cacheObj == null) {
            log.error("No cache named " + cacheName + " found among MBeans");
            return false;
         }
         Utils.saveSorted((Properties) mbeanServer.getAttribute(cacheObj, "configurationAsProperties"), dumpFile);
         return true;
      } catch (Exception e) {
         log.error("Error while dumping " + cacheName + " cache config as properties", e);
         return false;
      }
   }

   @Override
   public boolean dumpGlobal(File dumpFile, GlobalConfiguration globalConfiguration, String managerName) {
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         ObjectName objCacheManager = new ObjectName("jboss.infinispan:type=CacheManager,name=\"" + managerName + "\",component=CacheManager");
         Utils.saveSorted((Properties) mbeanServer.getAttribute(objCacheManager, "globalConfigurationAsProperties"), dumpFile);
         return true;
      } catch (Exception e) {
         log.error("Error while dumping global config as properties", e);
         return false;
      }
   }
}
