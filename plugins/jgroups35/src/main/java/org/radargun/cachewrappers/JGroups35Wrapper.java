package org.radargun.cachewrappers;

import org.jgroups.util.Util;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Set;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JGroups35Wrapper extends JGroupsWrapper {
   @Override
   public void tearDown() throws Exception {
      // the code is same but now it calls Util.close(Closeable)
      Util.close(ch);
      started = false;
   }

   @Override
   public void setUp(String configName, boolean isLocal, int nodeIndex, TypedProperties confAttributes) throws Exception {
      super.setUp(configName, isLocal, nodeIndex, confAttributes);
      if (confAttributes.getBooleanProperty("dumpConfig", false)) {
         String productName = confAttributes.getProperty("productName", "default");
         File dumpDir = new File("conf" + File.separator + "normalized" + File.separator + productName + File.separator + configName);
         if (!dumpDir.exists()) {
            dumpDir.mkdirs();
         }
         dumpConfig(new File(dumpDir, "config_jgroups.xml"));
      }
   }

   private boolean dumpConfig(File dumpFile) {
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName("jboss.infinispan:type=protocol,cluster=\"" + ch.getClusterName() + "\",protocol=*"), null);
         if (beanObjs.isEmpty()) {
            log.error("no JGroups protocols found");
            return false;
         }
         Properties p = new Properties();
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
         Utils.saveSorted(p, dumpFile);
         return true;
      } catch (Exception e) {
         log.error("Error while dumping JGroups config as properties", e);
         return false;
      }
   }
}
