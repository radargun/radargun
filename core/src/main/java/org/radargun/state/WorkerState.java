package org.radargun.state;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Map;

import org.radargun.RemoteWorkerConnection;
import org.radargun.config.Cluster;
import org.radargun.reporting.Timeline;
import org.radargun.utils.WorkerConnectionInfo;

/**
 * State residing on worker, passed to each's
 * {@link org.radargun.DistStage#initOnWorker(WorkerState)}
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class WorkerState extends StateBase<ServiceListener> {

   private InetAddress localAddress;
   private int workerIndex = -1;

   private String plugin;
   private String serviceName;
   private Cluster.Group group;

   private RemoteWorkerConnection.WorkerAddresses workerAddresses;
   private int indexInGroup;

   private Map<Class<?>, Object> traits;
   private Timeline timeline;

   public void setLocalAddress(InetAddress localAddress) {
      this.localAddress = localAddress;
   }

   public void setWorkerIndex(int workerIndex) {
      this.workerIndex = workerIndex;
   }

   public InetAddress getLocalAddress() {
      return localAddress;
   }

   public int getWorkerIndex() {
      return workerIndex;
   }

   @Override
   public void setCluster(Cluster cluster) {
      super.setCluster(cluster);
      group = cluster.getGroup(workerIndex);
      indexInGroup = cluster.getIndexInGroup(workerIndex);
   }

   public String getGroupName() {
      return group.name;
   }

   public int getGroupSize() {
      return group.size;
   }

   @Override
   public void reset() {
      super.reset();
      traits = null;
   }

   public String getPlugin() {
      return plugin;
   }

   public void setPlugin(String plugin) {
      this.plugin = plugin;
   }

   public String getServiceName() {
      return serviceName;
   }

   public void setService(String service) {
      this.serviceName = plugin + "/" + service;
   }

   public int getIndexInGroup() {
      return indexInGroup;
   }

   public Map<Class<?>, Object> getTraits() {
      return traits;
   }

   public void setTraits(Map<Class<?>, Object> traits) {
      this.traits = traits;
   }

   public <T> T getTrait(Class<? extends T> traitClass) {
      return (T) traits.get(traitClass);
   }

   public Timeline getTimeline() {
      return timeline;
   }

   public void setTimeline(Timeline timeline) {
      this.timeline = timeline;
   }

   public void setWorkerAddresses(RemoteWorkerConnection.WorkerAddresses workerAddresses) {
      this.workerAddresses = workerAddresses;
   }

   public WorkerConnectionInfo getWorkerAddresses(Cluster cluster, String groupName, int indexInGroup) {
      int indexTotal = (Integer) new ArrayList(cluster.getWorkers(groupName)).get(indexInGroup);
      return workerAddresses.getWorkerAddresses(indexTotal);
   }
}
