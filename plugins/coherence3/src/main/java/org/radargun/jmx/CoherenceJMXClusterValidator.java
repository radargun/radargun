package org.radargun.jmx;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.log4j.Logger;

/**
 * 
 * JMXClusterValidator for Oracle Coherence
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 * 
 */
public class CoherenceJMXClusterValidator implements JMXClusterValidator {
   private static final Logger log = Logger.getLogger(CoherenceJMXClusterValidator.class);

   private static final String OBJ_CLUSTER = "Coherence:type=Cluster";
   private static final String ATTR_CLUSTER_SIZE = "ClusterSize";
   private static final String ATTR_MEMBERS = "Members";

   public static class ClusterStatus {
      public int numAvailableNodes;
      public int numMembers;
      public Set<String> partitions;

      public ClusterStatus(int numAvailableNodes, int numMembers, Set<String> partitions) {
         super();
         this.numAvailableNodes = numAvailableNodes;
         this.numMembers = numMembers;
         this.partitions = partitions;
      }
   }

   private class CoherenceJMXPoller extends JMXPoller {

      public CoherenceJMXPoller(List<InetSocketAddress> jmxEndpoints, long queryTimeout, String serviceUrlTemplate) {
         super(jmxEndpoints, queryTimeout, serviceUrlTemplate);
      }

      public CoherenceJMXPoller(List<InetSocketAddress> jmxEndpoints, long queryTimeout) {
         super(jmxEndpoints, queryTimeout);
      }

      public ClusterStatus checkStatus() {
         List<Result> pollResults = poll();
         Set<String> partitions = new HashSet<String>();
         int numAvailableNodes = 0;
         int numMembers = -1;
         boolean numMembersEqual = true;
         log.trace("Number of results of polling: " + pollResults.size());
         for (int j = 0; j < pollResults.size(); j++) {
            Result r = pollResults.get(j);
            Object[] tuple = (Object[]) r.value;
            if (tuple != null) {
               partitions.add((String) tuple[0]);
               numAvailableNodes++;
               if (numMembers == -1) {
                  numMembers = (Integer) tuple[1];
               } else {
                  if (numMembers != (Integer) tuple[1]) {
                     numMembersEqual = false;
                  }
               }
            } else {
               if (log.isTraceEnabled()) {
                  if (r.connectError != null) {
                     log.trace("Connection error", r.connectError);
                  }
                  if (r.pollError != null) {
                     log.trace("Polling error", r.pollError);
                  }
               }
            }
         }
         return new ClusterStatus(numAvailableNodes, (numMembersEqual ? numMembers : -1), partitions);
      }

      protected Object[] pollNode(MBeanServerConnection connection, String node, int nodeIdx) throws Exception {
         ObjectName clusterObj = new ObjectName(OBJ_CLUSTER);
         Integer clusterSize = (Integer) connection.getAttribute(clusterObj, ATTR_CLUSTER_SIZE);
         String members = Arrays.asList((String[]) connection.getAttribute(clusterObj, ATTR_MEMBERS)).toString();
         return new Object[] { members, clusterSize };
      }

   }

   private List<InetSocketAddress> endpoints;
   private long jmxConnectionTimeout;

   @Override
   public void init(List<InetSocketAddress> slaveAddresses, long jmxConnectionTimeout, String prop1, String prop2,
         String prop3) {
      this.endpoints = slaveAddresses;
      this.jmxConnectionTimeout = jmxConnectionTimeout;
   }

   @Override
   public boolean waitUntilClusterFormed(long timeout) {
      if (timeout < 0) {
         log.info("Skipping waiting for cluster");
         return true;
      }
      CoherenceJMXPoller poller = null;
      try {
         poller = new CoherenceJMXPoller(endpoints, jmxConnectionTimeout);
         long giveUpTime = System.currentTimeMillis() + timeout;
         ClusterStatus status = poller.checkStatus();
         boolean formed = false;
         while (!(formed = isClusterFormed(status, endpoints.size())) && System.currentTimeMillis() < giveUpTime) {
            if (log.isDebugEnabled()) {
               log.debug("Cluster incomplete: " + getDebugStatus(status));
            }
            Thread.sleep(1000);
            status = poller.checkStatus();
         }
         if (formed) {
            log.debug("Cluster formed: " + status.partitions.iterator().next());
         }
         return formed;
      } catch (Exception e) {
         log.error("Error while waiting for cluster formation", e);
         return false;
      } finally {
         if (poller != null) {
            poller.closeConnections();
         }
      }
   }

   private boolean isClusterFormed(ClusterStatus status, int expectedClusterSize) {
      return status.numAvailableNodes == expectedClusterSize && status.numMembers == expectedClusterSize && status.partitions.size() == 1;
   }

   private String getDebugStatus(ClusterStatus status) {
      StringBuffer s = new StringBuffer("Available nodes: ");
      s.append(status.numAvailableNodes);
      s.append("\nCurrent views:\n\n");
      for (String view : status.partitions) {
         s.append(view);
         s.append("\n");
      }
      s.append("\n");
      return s.toString();
   }

}