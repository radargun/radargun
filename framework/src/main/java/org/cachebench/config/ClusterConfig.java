package org.cachebench.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Configuration for this cache instance.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClusterConfig
{

   public static final Log log = LogFactory.getLog(ClusterConfig.class);

   private int currentNodeIndex = -1;

   private int clusterSize = -1;

   private List<NodeAddress> members = new ArrayList<NodeAddress>();

   private String bindAddress;

   public int getCurrentNodeIndex()
   {
      if (currentNodeIndex == -1)
      {
         String serverindexStr = System.getProperty("currentIndex");
         try
         {
            currentNodeIndex = Integer.parseInt(serverindexStr);
         } catch (NumberFormatException e)
         {
            throw new IllegalStateException("Configuration 'currentIndex' is missing!");
         }
      }
      return currentNodeIndex;
   }

   public List<NodeAddress> getMembers()
   {
      if (members.size() > getClusterSize())
      {
         return members.subList(0, clusterSize);
      }
      return members;
   }

   public int getPortForThisNode()
   {
      NodeAddress address = getMembers().get(getCurrentNodeIndex());
      return Integer.parseInt(address.getPort());
   }

   public NodeAddress getAddressForThisNode()
   {
      return getMembers().get(getCurrentNodeIndex());
   }

   public int getClusterSize()
   {
      if (clusterSize < 0)
      {
         String clusterSizeStr = System.getProperty("clusterSize");
         if (clusterSizeStr != null)
         {
            int size = Integer.parseInt(clusterSizeStr);
            log.info("Received cluster size: " + size);
            clusterSize = size;
         }
         else
         {
            return members.size();
         }
      }
      return clusterSize;
   }

   public boolean isMaster()
   {
      return getCurrentNodeIndex() == 0;
   }

   public void addMember(NodeAddress member)
   {
      members.add(member);
   }

   public String getBindAddress()
   {
      if (bindAddress == null)
      {
         bindAddress = System.getProperties().getProperty("bind.address");
      }
      return bindAddress;
   }

   public void setBindAddress(String bindAddress)
   {
      this.bindAddress = bindAddress;
   }

   public List<InetSocketAddress> getMemberAddresses()
   {
      List<InetSocketAddress> result = new ArrayList();
      for (NodeAddress address : getMembers())
      {
         result.add(new InetSocketAddress(address.getHost(), address.getPortAsInt()));
      }
      return result;
   }

   public String toString()
   {
      return "{bindAddress:" + bindAddress + ", members:" + getMembers() + ", clusterSize:" + getClusterSize() + "}";
   }

   public void validateMembers()
   {
      List<InetSocketAddress> addressList = getMemberAddresses();
      Set addressSet = new HashSet(addressList);
      if (addressList.size() != addressSet.size())
      {
         throw new RuntimeException("There are memebers defined which point to the same host:port. Verify the configuration");
      }
   }
}
