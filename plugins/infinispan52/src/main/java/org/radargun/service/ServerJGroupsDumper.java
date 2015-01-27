package org.radargun.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Dumps JGroups information retrieved through JMX to log. Use for debug purposes only.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ServerJGroupsDumper implements Runnable {
   protected static final String CLUSTER_NAME = "cluster_name";
   protected static final Log log = LogFactory.getLog(ServerJGroupsDumper.class);
   protected static final String NAME = "name";
   private final InfinispanServerService service;

   private Map<ObjectName, String[]> attributeNameMap = new HashMap<>();

   public ServerJGroupsDumper(InfinispanServerService service) {
      this.service = service;
   }

   @Override
   public void run() {
      MBeanServerConnection connection = service.connection;
      if (connection != null) {
         try {
            dumpJGroups(connection);
         } catch (Exception e) {
            log.error("Failed to read JMX data", e);
         }
      }
   }

   protected void dumpJGroups(MBeanServerConnection connection) throws Exception {
      Set<ObjectName> channels = connection.queryNames(new ObjectName("jgroups:type=channel,cluster=*"), null);
      for (ObjectName channel : channels) {
         String clusterName = (String) connection.getAttribute(channel, CLUSTER_NAME);
         log.debugf("Channel for cluster %s:", clusterName);
         Set<ObjectName> protocols = connection.queryNames(
               new ObjectName(String.format("jgroups:type=protocol,cluster=\"%s\",protocol=*", clusterName)), null);
         for (ObjectName protocol : protocols) {
            String protocolName = (String) connection.getAttribute(protocol, NAME);
            log.debugf("%s:", protocolName);
            try {
               String[] attributeNames = attributeNameMap.get(protocol);
               if (attributeNames == null) {
                  ArrayList<String> names = new ArrayList<>();
                  for (MBeanAttributeInfo info : connection.getMBeanInfo(protocol).getAttributes()) {
                     if (info.isReadable()) {
                        names.add(info.getName());
                     }
                  }
                  attributeNames = names.toArray(new String[names.size()]);
                  Arrays.sort(attributeNames);
                  attributeNameMap.put(protocol, attributeNames);
               }
               AttributeList attributes = connection.getAttributes(protocol, attributeNames);
               for (Attribute attribute : attributes.asList()) {
                  log.debugf("\t%s = %s", attribute.getName(), attribute.getValue());
               }
            } catch (Exception e) {
               log.errorf("Failed to read data for %s: %s", protocolName, e.getMessage());
               log.trace("Stack trace: ", e);
            }
         }
      }
   }
}
