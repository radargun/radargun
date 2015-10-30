package org.radargun.state;

import java.util.HashMap;
import java.util.Map;

import org.radargun.config.Cluster;

/**
 * Support class for master and slave states.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class StateBase {

   private Map<String, Object> stateMap = new HashMap<>();
   private String configName;
   private Cluster cluster;
   private int clusterSize;
   private int maxClusterSize;

   public String getConfigName() {
      return configName;
   }

   public void setConfigName(String configName) {
      this.configName = configName;
   }

   public int getClusterSize() {
      return clusterSize;
   }

   public Cluster getCluster() {
      return cluster;
   }

   public void setCluster(Cluster cluster) {
      this.cluster = cluster;
      this.clusterSize = cluster.getSize();
   }

   public int getMaxClusterSize() {
      return maxClusterSize;
   }

   public void setMaxClusterSize(int maxClusterSize) {
      this.maxClusterSize = maxClusterSize;
   }

   public Object remove(Object key) {
      return stateMap.remove(key);
   }

   /**
    * Store an entry that can be retrieved in next stages. However, this value does not persist service
    * destruction and therefore cannot be retrieved in different configuration.
    * @param key
    * @param value
    */
   public void put(String key, Object value) {
      stateMap.put(key, value);
   }

   public Object get(String key) {
      return stateMap.get(key);
   }

   public String getString(Object key) {
      return (String) stateMap.get(key);
   }

   public Integer getInteger(Object key) {
      Object value = stateMap.get(key);
      if (value == null) return null;
      if (value instanceof Integer) {
         return (Integer) value;
      } else {
         return Integer.parseInt(value.toString());
      }
   }

   public Float getFloat(Object key) {
      Object value = stateMap.get(key);
      if (value == null) return null;
      if (value instanceof Float) {
         return (Float) value;
      } else {
         return Float.parseFloat(value.toString());
      }
   }

   public Boolean getBoolean(Object key) {
      Object value = stateMap.get(key);
      if (value == null) return null;
      if (value instanceof Boolean) {
         return (Boolean) value;
      } else {
         return Boolean.parseBoolean(value.toString());
      }
   }

   public Map<String, String> asStringMap() {
      HashMap<String, String> map = new HashMap<>(stateMap.size());
      for (Map.Entry<String, Object> entry : stateMap.entrySet()) {
         map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
      }
      return map;
   }

   public void reset() {
      stateMap.clear();
   }
}
