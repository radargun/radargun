package org.radargun.jmx;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.TimeService;

/**
 *
 * Periodically polls for values exposed via JMX on multiple nodes.
 *
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 *
 */
public abstract class JMXPoller implements NotificationListener {
   public static final String DEFAULT_SERVICE_URL_TEMPLATE = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
   private static Log log = LogFactory.getLog(JMXPoller.class);

   private List<InetSocketAddress> jmxEndpoints;
   private long queryTimeout;
   private ConcurrentHashMap<InetSocketAddress, JMXConnector> connectors;
   private String serviceUrlTemplate;

   public static class Result {
      public Exception connectError;
      public Exception pollError;
      public Object value;

      public Result(Exception connectError, Exception pollError, Object value) {
         this.connectError = connectError;
         this.pollError = pollError;
         this.value = value;
      }
   }

   /**
    *
    * Create a new JMXPoller.
    *
    * @param jmxEndpoints
    *           jmx endpoints in form host:port
    * @param logicalNodeNames
    *           logical node names, list corresponding to jmx endpoints with size and positions. may
    *           be null, in that case logical names won't be used.
    * @param queryTimeout
    * @param serviceUrlTemplate
    */
   protected JMXPoller(List<InetSocketAddress> jmxEndpoints, long queryTimeout, String serviceUrlTemplate) {
      this.jmxEndpoints = jmxEndpoints;
      this.queryTimeout = queryTimeout;
      this.connectors = new ConcurrentHashMap<InetSocketAddress, JMXConnector>(jmxEndpoints.size());
      this.serviceUrlTemplate = serviceUrlTemplate;
   }

   protected JMXPoller(List<InetSocketAddress> jmxEndpoints, long queryTimeout) {
      this(jmxEndpoints, queryTimeout, DEFAULT_SERVICE_URL_TEMPLATE);
   }

   /**
    * Override to poll for certain JMX attributes.
    *
    * @param connection
    *           Connection to a JMX endpoint.
    * @param nodeName
    *           Logical node name, if logical node names are not used, then endpoint string
    *           host:port;
    * @return Custom response object.
    * @throws Exception
    */
   protected abstract Object pollNode(MBeanServerConnection connection, String nodeName, int nodeIdx) throws Exception;

   protected String endpointToString(InetSocketAddress endpoint) {
      return endpoint.getHostName() + ":" + endpoint.getPort();
   }


   private void discardConnector(InetSocketAddress endpoint, JMXConnector connector) {
      connectors.remove(endpoint);
      if (connector != null) {
         try {
            connector.close();
         } catch (IOException e1) {
            log.trace("Error while closing connector", e1);
         }
      }
   }

   public synchronized List<Result> poll() {
      final Result[] results = new Result[jmxEndpoints.size()];
      Thread[] tryPoll = new Thread[jmxEndpoints.size()];
      for (int i = 0; i < jmxEndpoints.size(); i++) {
         final InetSocketAddress endpoint = jmxEndpoints.get(i);
         final String node = endpointToString(endpoint);
         final int nodeIdx = i;
         tryPoll[i] = new Thread("tryPoll-" + node) {
            @Override
            public void run() {
               JMXConnector connector = null;
               MBeanServerConnection connection = null;
               try {
                  connector = connect(endpoint);
                  connection = connector.getMBeanServerConnection();
               } catch (Exception e) {
                  log.trace("Discarding connector to endpoint " + endpoint + " because of an exception.", e);
                  discardConnector(endpoint, connector);
                  results[nodeIdx] = new Result(e, null, null);
                  return;
               }
               try {
                  results[nodeIdx] = new Result(null, null, pollNode(connection, node, nodeIdx));
               } catch (Exception e) {
                  discardConnector(endpoint, connector);
                  results[nodeIdx] = new Result(null, e, null);
               }
            }
         };
         tryPoll[i].start();
      }
      long waitEnd = TimeService.currentTimeMillis() + queryTimeout;
      boolean broken = false;
      for (int i = 0; i < tryPoll.length; i++) {
         try {
            long maxJoinWait = waitEnd - TimeService.currentTimeMillis();
            if (maxJoinWait <= 0) {
               broken = true;
               break;
            }
            tryPoll[i].join(maxJoinWait);
         } catch (InterruptedException e) {
            if (results[i] == null) {
               results[i] = new Result(null, e, null);
            }
         }
      }
      if (broken) {
         for (int i = 0; i < tryPoll.length; i++) {
            tryPoll[i].interrupt();
         }
      }
      // return current snapshot of the map, the results map may get modified
      // by an unfinished thread
      Result[] a = Arrays.copyOf(results, results.length);
      for (int i = 0; i < a.length; i++) {
         if (a[i] == null) {
            a[i] = new Result(null, null, null);
         }
      }
      return Arrays.asList(a);
   }

   public synchronized void closeConnections() {
      final Set<JMXConnector> connectors1 = new HashSet<JMXConnector>(connectors.values());
      connectors.clear();
      new Thread(new Runnable() {
         @Override
         public void run() {
            for (JMXConnector ctor : connectors1) {
               try {
                  ctor.close();
               } catch (Exception e) {
                  log.trace("Error while closing JMXConnector", e);
               }
            }
         }
      }, "JMXPoller.closeConnections").start();
   }

   private JMXConnector connect(final InetSocketAddress endpoint) throws Exception {
      JMXConnector cachedConnector = connectors.get(endpoint);
      if (cachedConnector != null) {
         return cachedConnector;
      }
      JMXServiceURL serviceURL = new JMXServiceURL(String.format(this.serviceUrlTemplate, endpoint.getHostName(),
         endpoint.getPort()));
      JMXConnector newConnector = JMXConnectorFactory.newJMXConnector(serviceURL, null);
      try {
         newConnector.connect();
      } catch (Exception e) {
         newConnector.close();
         throw e;
      }
      if (log.isTraceEnabled()) {
         log.trace("created new connector " + newConnector + " to " + endpoint);
      }
      JMXConnector oldConnector = connectors.putIfAbsent(endpoint, newConnector);
      if (oldConnector != null) {
         newConnector.close();
         cachedConnector = oldConnector;
      } else {
         NotificationFilterSupport closedFilter = new NotificationFilterSupport();
         closedFilter.enableType(JMXConnectionNotification.CLOSED);
         newConnector.addConnectionNotificationListener(this, closedFilter, endpoint);
         cachedConnector = newConnector;
      }
      return cachedConnector;
   }

   public List<InetSocketAddress> getEndpoints() {
      return jmxEndpoints;
   }

   @Override
   public void handleNotification(Notification notification, Object node) {
      if (log.isTraceEnabled()) {
         log.trace("Notification received: " + notification + " handback: " + node);
      }
      connectors.remove(node);
   }

}