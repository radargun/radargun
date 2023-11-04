package org.radargun.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.Util;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Clustered;
import org.radargun.traits.ConfigurationProvider;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.Utils;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups42Service implements Lifecycle, Clustered, BasicOperations.Cache {

   protected static Log log = LogFactory.getLog(JGroups36Service.class);

   protected JChannel ch;

   @Property(doc = "If a message is sent to all nodes")
   protected boolean replicated;

   @Property(name = "file", doc = "Configuration file for JGroups.", deprecatedName = "config")
   protected String configFile;

   protected JGroupsReceiver receiver;
   protected JGroupsCacheOperation jGroupsCacheOperation;

   public JGroups42Service() {
   }

   public JGroups42Service configFile(String file) {
      this.configFile = file;
      return this;
   }

   @ProvidesTrait
   public JGroups42Service getSelf() {
      return this;
   }

   @ProvidesTrait
   public BasicOperations createOperations() {
      return new BasicOperations() {
         @Override
         public <K, V> Cache<K, V> getCache(String cacheName) {
            return JGroups42Service.this;
         }
      };
   }

   @Override
   public void start() {

      log.info("Loading JGroups form: " + org.jgroups.Version.class.getProtectionDomain().getCodeSource().getLocation());
      log.info("JGroups version: " + org.jgroups.Version.printDescription());

      try {
         ch = new JChannel(configFile);
         receiver = new JGroups36Receiver(ch);
         setReceiver();
         connectChannel("x");
         createCacheOperation();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   protected void setReceiver() {
      ch.setReceiver(new ReceiverAdapter() {
         public void viewAccepted(View newView) {
            receiver.viewAccepted(newView);
         }
         public void receive(Message msg) {
         }
      });
   }

   protected void createCacheOperation() {
      jGroupsCacheOperation = new JGroups42CacheOperationImpl(ch);
   }

   protected void connectChannel(String clusterName) throws Exception {
      // binary compatibility
      ch.connect(clusterName);
   }

   @Override
   public void stop() {
      Util.close(ch);
      synchronized (this) {
         receiver.getMembershipHistory().add(Membership.empty());
      }
   }

   @Override
   public boolean isRunning() {
      return ch != null && ch.isConnected();
   }

   @Override
   public Object get(Object key) {
      throw new IllegalStateException("not implemented");
   }

   @Override
   public boolean containsKey(Object key) {
      throw new IllegalStateException("not implemented");
   }

   @Override
   public void put(Object key, Object value) {
      try {
         if (replicated) {
            jGroupsCacheOperation.replicatedPut(key, value);
         } else {
            throw new IllegalStateException("not implemented");
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public Object getAndPut(Object key, Object value) {
      throw new IllegalStateException("not implemented");
   }

   @Override
   public boolean remove(Object key) {
      throw new IllegalStateException("not implemented");
   }

   @Override
   public Object getAndRemove(Object key) {
      throw new IllegalStateException("not implemented");
   }

   @Override
   public void clear() {
      throw new IllegalStateException("not implemented");
   }

   @Override
   public boolean isCoordinator() {
      View view = ch.getView();
      return view == null || view.getMembers() == null || view.getMembers().isEmpty()
            || ch.getAddress().equals(view.getMembers().get(0));
   }

   @Override
   public synchronized Collection<Member> getMembers() {
      if (receiver.getMembershipHistory().isEmpty()) return null;
      return receiver.getMembershipHistory().get(receiver.getMembershipHistory().size() - 1).members;
   }

   @Override
   public synchronized List<Membership> getMembershipHistory() {
      return new ArrayList<>(receiver.getMembershipHistory());
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

   protected Properties dumpProperties() {
      Properties p = new Properties();
      try {
         MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
         String objName = String.format("jboss.infinispan:type=protocol,cluster=\"%s\",protocol=*", ch.getClusterName());
         Set<ObjectInstance> beanObjs = mbeanServer.queryMBeans(new ObjectName(objName), null);
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
