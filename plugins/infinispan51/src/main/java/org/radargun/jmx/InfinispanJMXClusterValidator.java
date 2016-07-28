package org.radargun.jmx;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.TimeService;

/**
 * JMXClusterValidator for Infinispan
 *
 * @author Michal Linhard &lt;mlinhard@redhat.com&gt;
 */
public class InfinispanJMXClusterValidator implements JMXClusterValidator {
   private static final Log log = LogFactory.getLog(InfinispanJMXClusterValidator.class);

   private static final String ATTR_VIEW = "View";
   private static final String ATTR_NUM_MEMBERS = "NumMembers";
   private static final String ATTR_STM_JOIN_COMPLETE = "JoinComplete";
   private static final String ATTR_STM_STATE_TRANSFER_IN_PROGRESS = "StateTransferInProgress";

   public static class ClusterStatus {
      public int numAvailableNodes;
      public int numMembers;
      public Set<String> jgroupsViews;
      public Set<String> incompleteJoinNodes;
      public Set<String> incompleteStateTransferNodes;

      public ClusterStatus(int numAvailableNodes, int numMembers, Set<String> partitions,
                           Set<String> incompleteJoinNodes, Set<String> incompleteStateTransferNodes) {
         super();
         this.numAvailableNodes = numAvailableNodes;
         this.numMembers = numMembers;
         this.jgroupsViews = partitions;
         this.incompleteJoinNodes = incompleteJoinNodes;
         this.incompleteStateTransferNodes = incompleteStateTransferNodes;
      }
   }

   private class InfinispanJMXPoller extends JMXPoller {

      public InfinispanJMXPoller(List<InetSocketAddress> jmxEndpoints, long queryTimeout, String serviceUrlTemplate) {
         super(jmxEndpoints, queryTimeout, serviceUrlTemplate);
      }

      public InfinispanJMXPoller(List<InetSocketAddress> jmxEndpoints, long queryTimeout) {
         super(jmxEndpoints, queryTimeout);
      }

      public ClusterStatus checkStatus() {
         List<Result> pollResults = poll();
         Set<String> partitions = new HashSet<String>();
         Set<String> incompleteJoinNodes = new HashSet<String>();
         Set<String> incompleteStateTransferNodes = new HashSet<String>();
         int numAvailableNodes = 0;
         int numMembers = -1;
         boolean numMembersEqual = true;
         log.trace("Number of results of polling: " + pollResults.size());
         for (int j = 0; j < pollResults.size(); j++) {
            Result r = pollResults.get(j);
            Object[] tuple = (Object[]) r.value;
            if (tuple != null) {
               partitions.add((String) tuple[0]);
               if (!((Boolean) tuple[2])) {
                  incompleteJoinNodes.add(getEndpoints().get(j).toString());
               }
               if ((Boolean) tuple[3]) {
                  incompleteStateTransferNodes.add(getEndpoints().get(j).toString());
               }
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
         return new ClusterStatus(numAvailableNodes, (numMembersEqual ? numMembers : -1), partitions,
            incompleteJoinNodes, incompleteStateTransferNodes);
      }

      protected Object[] pollNode(MBeanServerConnection connection, String node, int nodeIdx) throws Exception {
         String view = (String) connection.getAttribute(new ObjectName(channelObjectName), ATTR_VIEW);
         Integer numMembers = (Integer) connection
            .getAttribute(new ObjectName(gmsProtocolObjectName), ATTR_NUM_MEMBERS);
         Boolean joinComplete = (Boolean) connection.getAttribute(new ObjectName(stateTransferMgrObjectName),
            ATTR_STM_JOIN_COMPLETE);
         Boolean stInProgres = (Boolean) connection.getAttribute(new ObjectName(stateTransferMgrObjectName),
            ATTR_STM_STATE_TRANSFER_IN_PROGRESS);
         return new Object[] {view, numMembers, joinComplete, stInProgres};
      }

   }

   private String channelObjectName;
   private String gmsProtocolObjectName;
   private String stateTransferMgrObjectName;
   private List<InetSocketAddress> endpoints;
   private long jmxConnectionTimeout;

   @Override
   public void init(List<InetSocketAddress> slaveAddresses, long jmxConnectionTimeout, String prop1, String prop2,
                    String prop3) {
      this.endpoints = slaveAddresses;
      this.jmxConnectionTimeout = jmxConnectionTimeout;
      channelObjectName = prop1;
      gmsProtocolObjectName = prop2;
      stateTransferMgrObjectName = prop3;
   }

   @Override
   public boolean waitUntilClusterFormed(long timeout) {
      if (timeout < 0) {
         log.info("Skipping waiting for cluster");
         return true;
      }
      InfinispanJMXPoller poller = null;
      try {
         poller = new InfinispanJMXPoller(endpoints, jmxConnectionTimeout);
         long giveUpTime = TimeService.currentTimeMillis() + timeout;
         ClusterStatus status = poller.checkStatus();
         boolean formed = false;
         while (!(formed = isClusterFormed(status, endpoints.size())) && TimeService.currentTimeMillis() < giveUpTime) {
            if (log.isDebugEnabled()) {
               log.debug("Cluster incomplete: " + getDebugStatus(status));
            }
            Thread.sleep(1000);
            status = poller.checkStatus();
         }
         if (formed) {
            log.info("Cluster formed: " + status.jgroupsViews.iterator().next());
         } else {
            log.error("Cluster failed to form, last status: " + getDebugStatus(status));
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
      return status.numAvailableNodes == expectedClusterSize && status.numMembers == expectedClusterSize
         && status.jgroupsViews.size() == 1 && status.incompleteJoinNodes.isEmpty()
         && status.incompleteStateTransferNodes.isEmpty();
   }

   private String getDebugStatus(ClusterStatus status) {
      StringBuffer s = new StringBuffer("Available nodes: ");
      s.append(status.numAvailableNodes);
      s.append("\nCurrent views:\n\n");
      for (String view : status.jgroupsViews) {
         s.append(view);
         s.append("\n");
      }
      s.append("\nNodes with incomplete Join:\n\n");
      s.append(status.incompleteJoinNodes);
      s.append("\nNodes with incomplete State transfer:\n\n");
      s.append(status.incompleteStateTransferNodes);
      s.append("\n");
      return s.toString();
   }

}