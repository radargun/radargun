package org.radargun.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.infinispan.client.hotrod.Search;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.remote.ProtobufMetadataManager;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Utils;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanHotrodQueryable extends AbstractInfinispanQueryable {
   protected static final String REMOTING_JMX_SERVICE_URL_TEMPLATE = "service:jmx:remoting-jmx://%s:%d";
   protected static final Log log = LogFactory.getLog(InfinispanHotrodQueryable.class);

   protected final Infinispan60HotrodService service;

   public InfinispanHotrodQueryable(Infinispan60HotrodService service) {
      this.service = service;
   }

   @Override
   public QueryBuilder getBuilder(String cacheName, Class<?> clazz) {
      if (cacheName == null) {
         cacheName = service.cacheName;
      }
      QueryFactory factory = Search.getQueryFactory(
            cacheName == null ? service.managerForceReturn.getCache() : service.managerForceReturn.getCache(cacheName));
      return new QueryBuilderImpl(factory, clazz);
   }

   @Override
   public void reindex(String containerName) {
      // We should rather throw an exception because if the cache is configured
      // with manual index we cannot make sure that the cache will be reindexed.
      throw new UnsupportedOperationException();
   }

   protected void registerProtofilesLocal(SerializationContext context) {
      for (String protofile : service.protofiles) {
         try {
            context.registerProtofile(protofile);
         } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read protofile " + protofile, e);
         }
      }
   }

   protected void registerProtofilesRemote() {
      JMXConnector connector = null;
      MBeanServerConnection connection = null;
      for (String host : service.serverHostnames) {
         JMXServiceURL serviceURL;
         try {
            serviceURL = new JMXServiceURL(String.format(REMOTING_JMX_SERVICE_URL_TEMPLATE, host, service.jmxPort));
         } catch (MalformedURLException e) {
            log.error("Failed to form JMX URL", e);
            continue;
         }
         try {
            connector = JMXConnectorFactory.newJMXConnector(serviceURL, null);
            connector.connect();
            connection = connector.getMBeanServerConnection();
            log.info("Connected via JMX to " + host + " (" + serviceURL + ")");
         } catch (IOException e) {
            log.error("Failed to connect to " + serviceURL, e);
            closeConnector(connector);
            continue;
         }
         break;
      }
      if (connection == null) {
         throw new IllegalStateException("Cannot connect to any server.");
      }

      try {
         ObjectName objName = null;
         try {
            objName = new ObjectName(service.jmxDomain + ":type=RemoteQuery,name="
                  + ObjectName.quote(service.clusterName)
                  + ",component=" + ProtobufMetadataManager.OBJECT_NAME);
         } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Failed to register protofiles", e);
         }

         for (String protofile : service.protofiles) {
            byte[] descriptor;
            try (InputStream inputStream = getClass().getResourceAsStream(protofile)) {
               descriptor = Utils.readAsBytes(inputStream);
            } catch (IOException e) {
               throw new IllegalStateException("Failed to read protofile " + protofile, e);
            }
            try {
               connection.invoke(objName, "registerProtofile", new Object[]{descriptor}, new String[]{byte[].class.getName()});
               log.info("Protofile " + protofile + " registered.");
            } catch (Exception e) {
               throw new IllegalStateException("Failed to register protofile " + protofile, e);
            }
         }
      } finally {
         closeConnector(connector);
      }
   }

   protected void closeConnector(JMXConnector connector) {
      if (connector != null) {
         try {
            connector.close();
         } catch (IOException e) {
            log.error("Failed to close connector ", e);
         }
      }
   }
}
