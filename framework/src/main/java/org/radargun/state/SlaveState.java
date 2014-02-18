package org.radargun.state;

import java.net.InetAddress;

import org.radargun.CacheWrapper;
import org.radargun.utils.ClassLoadHelper;

/**
 * State residing on slave, passed to each's {@link org.radargun.DistStage#initOnSlave(SlaveState)}
 *
 * @author Mircea.Markus@jboss.com
 */
public class SlaveState extends StateBase {

   private InetAddress localAddress;
   private CacheWrapper cacheWrapper;
   private int slaveIndex = -1;
   private int groupSize = 0;
   private String plugin;
   private ClassLoadHelper classLoadHelper;

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

   public void setCacheWrapper(CacheWrapper wrapper) {
      this.cacheWrapper = wrapper;
   }

   public CacheWrapper getCacheWrapper() {
      return cacheWrapper;
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
}
