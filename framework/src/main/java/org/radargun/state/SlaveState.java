package org.radargun.state;

import java.net.InetAddress;
import java.util.Map;

import org.radargun.utils.ClassLoadHelper;

/**
 * State residing on slave, passed to each's {@link org.radargun.DistStage#initOnSlave(SlaveState)}
 *
 * @author Mircea.Markus@jboss.com
 */
public class SlaveState extends StateBase {

   private InetAddress localAddress;
   private int slaveIndex = -1;
   private int groupSize = 0;
   private int groupCount = 1;
   private String plugin;
   private ClassLoadHelper classLoadHelper;
   private String serviceName;
   private Object service;
   private int indexInGroup;
   private Map<Class<?>, Object> traits;

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

   public void setGroupSize(int groupSize) {
      this.groupSize = groupSize;
   }

   public int getGroupSize() {
      return groupSize;
   }

   public ClassLoadHelper getClassLoadHelper() {
      return classLoadHelper;
   }

   public String getPlugin() {
      return plugin;
   }

   public void setPlugin(String plugin) {
      this.plugin = plugin;
      classLoadHelper = new ClassLoadHelper(true, this.getClass(), plugin, this);
   }

   public String getServiceName() {
      return serviceName;
   }

   public void setService(String service) {
      this.serviceName = plugin + "/" + serviceName;
   }

   public int getGroupCount() {
      return groupCount;
   }

   public void setGroupCount(int groupCount) {
      this.groupCount = groupCount;
   }

   public int getIndexInGroup() {
      return indexInGroup;
   }

   public void setIndexInGroup(int indexInGroup) {
      this.indexInGroup = indexInGroup;
   }

   public void setTraits(Map<Class<?>, Object> traits) {
      this.traits = traits;
   }

   public <T> T getTrait(Class<? extends T> traitClass) {
      return (T) traits.get(traitClass);
   }
}
