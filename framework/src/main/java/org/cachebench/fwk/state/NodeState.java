package org.cachebench.fwk.state;

import org.cachebench.CacheWrapper;

import java.net.InetAddress;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class NodeState extends StateBase {
   private int nodeIndex;
   private int clusterSize;

   private InetAddress serverAddress;
   private InetAddress localAddress;
   private CacheWrapper cacheWrapper;


   public int getNodeIndex() {
      return nodeIndex;
   }

   public void setNodeIndex(int nodeIndex) {
      this.nodeIndex = nodeIndex;
   }

   public int getClusterSize() {
      return clusterSize;
   }

   public void setClusterSize(int clusterSize) {
      this.clusterSize = clusterSize;
   }

   public InetAddress getServerAddress() {
      return serverAddress;
   }

   public void setServerAddress(InetAddress serverAddress) {
      this.serverAddress = serverAddress;
   }

   public InetAddress getLocalAddress() {
      return localAddress;
   }

   public void setLocalAddress(InetAddress localAddress) {
      this.localAddress = localAddress;
   }

   public void setCacheWrapper(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
   }

   public CacheWrapper getCacheWrapper() {
      return cacheWrapper;
   }
}
