package org.radargun.service;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Extracts configuration to properties. Valid for caches since 5.2
 *
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
public class ConfigDumpHelper {

   protected static Log log = LogFactory.getLog(ConfigDumpHelper.class);
   private static Method plainToString = null;

   static {
      try {
         plainToString = Object.class.getMethod("toString");
      } catch (Exception e) {
         log.error("Error while initializing", e);
      }
   }

   public Properties dumpGlobal(GlobalConfiguration globalConfiguration, String jmxDomain, String managerName) {
      Properties properties = new Properties();
      try {
         reflect(globalConfiguration, properties, null);
      } catch (Exception e) {
         log.error("Error while dumping global config as properties", e);
      }
      return properties;
   }

   public Properties dumpCache(Configuration configuration, String domain, String managerName, String cacheName) {
      Properties properties = new Properties();
      try {
         reflect(configuration, properties, null);
      } catch (Exception e) {
         log.error("Error while dumping " + cacheName + " cache config as properties", e);
      }
      return properties;
   }

   public Properties dumpJGroups(String jmxDomain, String clusterName) {
      Properties properties = new Properties();
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName(String.format("%s:type=protocol,cluster=\"%s\",protocol=*", jmxDomain, clusterName)), null);
         if (beanObjs.isEmpty()) {
            log.error("no JGroups protocols found");
            return properties;
         }
         for (ObjectInstance beanObj : beanObjs) {
            ObjectName protocolObjectName = beanObj.getObjectName();
            MBeanInfo protocolBean = mbeanServer.getMBeanInfo(protocolObjectName);
            String protocolName = protocolObjectName.getKeyProperty("protocol");
            for (MBeanAttributeInfo info : protocolBean.getAttributes()) {
               String propName = info.getName();
               Object propValue = mbeanServer.getAttribute(protocolObjectName, propName);
               properties.setProperty(protocolName + "." + propName, propValue == null ? "null" : propValue.toString());
            }
         }
      } catch (Exception e) {
         log.error("Error while dumping JGroups config as properties", e);
      }
      return properties;
   }

   private List<Method> getMethods(Class<?> clazz) {
      Class<?> c = clazz;
      List<Method> r = new ArrayList<Method>();
      while (c != null && c != Object.class) {
         for (Method m : c.getDeclaredMethods()) {
            r.add(m);
         }
         c = c.getSuperclass();
      }
      return r;
   }

   private boolean hasPlainToString(Class<?> cls, Object obj) {
      try {
         if (cls.getMethod("toString") == plainToString) {
            return true;
         }
         String plainToStringValue = cls.getName() + "@" + Integer.toHexString(System.identityHashCode(obj));
         return plainToStringValue.equals(obj.toString());
      } catch (Exception e) {
         return false;
      }
   }

   /**
    * This is a generic method for reflection copied from PropertyFormatter from Infinispan 6.0
    *
    * @param obj
    * @param p
    * @param prefix
    */
   private void reflect(Object obj, Properties p, String prefix) {
      try {
         if (obj == null) {
            p.put(prefix, "null");
            return;
         }
         Class<?> cls = obj.getClass();
         if (cls.getName().startsWith("org.infinispan.config") && !cls.isEnum()) {
            for (Method m : getMethods(obj.getClass())) {
               if (m.getParameterTypes().length != 0 || "toString".equals(m.getName()) || "hashCode".equals(m.getName()) || "toProperties".equals(m.getName())) {
                  continue;
               }
               try {
                  String prefixDot = prefix == null || "".equals(prefix) ? "" : prefix + ".";
                  reflect(m.invoke(obj), p, prefixDot + m.getName());
               } catch (IllegalAccessException e) {
                  // ok
               }
            }
         } else if (Collection.class.isAssignableFrom(cls)) {
            Collection<?> collection = (Collection<?>) obj;
            Iterator<?> iter = collection.iterator();
            for (int i = 0; i < collection.size(); i++) {
               reflect(iter.next(), p, prefix + "[" + i + "]");
            }
         } else if (cls.isArray()) {
            Object[] a = (Object[]) obj;
            for (int i = 0; i < a.length; i++) {
               reflect(a[i], p, prefix + "[" + i + "]");
            }
         } else if (hasPlainToString(cls, obj)) {
            // we have a class that doesn't have a nice toString implementation
            p.put(prefix, cls.getName());
         } else {
            // we have a single value
            p.put(prefix, obj.toString());
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

}
