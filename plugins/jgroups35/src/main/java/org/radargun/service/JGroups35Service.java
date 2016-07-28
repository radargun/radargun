package org.radargun.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.jgroups.util.Util;
import org.radargun.Service;
import org.radargun.traits.ConfigurationProvider;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups35Service extends JGroupsService {
   @Override
   public void stop() {
      // the code is same but now it calls Util.close(Closeable)
      Util.close(ch);
      started = false;
   }

   @ProvidesTrait
   ConfigurationProvider getConfigurationProvider() {
      return new ConfigurationProvider() {
         @Override
         public Map<String, Properties> getNormalizedConfigs() {
            return Collections.singletonMap("jgroups", dumpProperties());
         }

         @Override
         public Map<String, byte[]> getOriginalConfigs() {
            InputStream stream = null;
            try {
               stream = getClass().getResourceAsStream(configFile);
               if (stream == null) {
                  stream = new FileInputStream(configFile);
               }
               return Collections.singletonMap(configFile, Utils.readAsBytes(stream));
            } catch (IOException e) {
               log.error("Cannot read configuration file " + configFile, e);
               return Collections.EMPTY_MAP;
            } finally {
               Utils.close(stream);
            }
         }
      };
   }

   private Properties dumpProperties() {
      Properties p = new Properties();
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName("jboss.infinispan:type=protocol,cluster=\"" + ch.getClusterName() + "\",protocol=*"), null);
         if (beanObjs.isEmpty()) {
            log.error("no JGroups protocols found");
            return p;
         }
         for (ObjectInstance beanObj : beanObjs) {
            ObjectName protocolObjectName = beanObj.getObjectName();
            MBeanInfo protocolBean = mbeanServer.getMBeanInfo(protocolObjectName);
            String protocolName = protocolObjectName.getKeyProperty("protocol");
            for (MBeanAttributeInfo info : protocolBean.getAttributes()) {
               String propName = info.getName();
               Object propValue = mbeanServer.getAttribute(protocolObjectName, propName);
               p.setProperty(protocolName + "." + propName, propValue == null ? "null" : propValue.toString());
            }
         }
         return p;
      } catch (Exception e) {
         log.error("Error while dumping JGroups config as properties", e);
         return p;
      }
   }
}
