/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA. 
 */
package org.radargun.cachewrappers;

import java.io.File;
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.radargun.utils.Utils;

/**
 * Extracts configuration to properties. Valid for caches since 5.2
 * 
 * @author Michal Linhard (mlinhard@redhat.com)
 * 
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

   public boolean dumpGlobal(File dumpFile, GlobalConfiguration globalConfiguration, String managerName) {
      try {
         Properties properties = new Properties();
         reflect(globalConfiguration, properties, null);
         Utils.saveSorted(properties, dumpFile);
         return true;
      } catch (Exception e) {
         log.error("Error while dumping global config as properties", e);
         return false;
      }
   }

   public boolean dumpCache(File dumpFile, Configuration configuration, String managerName, String cacheName) {
      try {
         Properties properties = new Properties();
         reflect(configuration, properties, null);
         Utils.saveSorted(properties, dumpFile);
         return true;
      } catch (Exception e) {
         log.error("Error while dumping " + cacheName + " cache config as properties", e);
         return false;
      }
   }

   public boolean dumpJGroups(File dumpFile, String clusterName) {
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName("jboss.infinispan:type=protocol,cluster=\"default\",protocol=*"), null);
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
    * 
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
