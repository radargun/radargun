package org.radargun.state;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.radargun.config.Cluster;
import org.radargun.reporting.Timeline;
import org.radargun.utils.Utils;

/**
 * State residing on slave, passed to each's {@link org.radargun.DistStage#initOnSlave(SlaveState)}
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class SlaveState extends StateBase {

   private InetAddress localAddress;
   private int slaveIndex = -1;
   private String plugin;
   private ClassLoader classLoader;
   private String serviceName;
   private Cluster cluster;
   private Cluster.Group group;
   private int indexInGroup;
   private Map<Class<?>, Object> traits;
   private Timeline timeline;
   private List<ServiceListener> serviceListeners = new CopyOnWriteArrayList<ServiceListener>();

   public void setLocalAddress(InetAddress localAddress) {
      this.localAddress = localAddress;
   }

   public void setSlaveIndex(int slaveIndex) {
      this.slaveIndex = slaveIndex;
   }

   public InetAddress getLocalAddress() {
      return localAddress;
   }

   public int getSlaveIndex() {
      return slaveIndex;
   }

   @Override
   public void setCluster(Cluster cluster) {
      super.setCluster(cluster);
      this.cluster = cluster;
      group = cluster.getGroup(slaveIndex);
      indexInGroup = cluster.getIndexInGroup(slaveIndex);
   }

   public String getGroupName() {
      return group.name;
   }

   public int getGroupSize() {
      return group.size;
   }

   public ClassLoader getClassLoader() {
      return classLoader;
   }

   public void reset() {
      traits.clear();
      serviceListeners.clear();
      classLoader = getClass().getClassLoader();
   }

   public String getPlugin() {
      return plugin;
   }

   public void setPlugin(String plugin) {
      this.plugin = plugin;
      classLoader = Utils.buildPluginSpecificClassLoader(plugin, getClass().getClassLoader());
   }

   public String getServiceName() {
      return serviceName;
   }

   public void setService(String service) {
      this.serviceName = plugin + "/" + service;
   }

   public String getGroupName(int slaveIndex) {
      return cluster.getGroup(slaveIndex).name;
   }

   public int getGroupCount() {
      return cluster.getGroups().size();
   }

   public int getIndexInGroup() {
      return indexInGroup;
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

   public void addServiceListener(ServiceListener listener) {
      serviceListeners.add(listener);
   }

   public void removeServiceListener(ServiceListener listener) {
      serviceListeners.remove(listener);
   }

   public Iterable<ServiceListener> getServiceListeners() {
      return Collections.unmodifiableCollection(serviceListeners);
   }
}
