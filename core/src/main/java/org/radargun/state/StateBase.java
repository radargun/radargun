package org.radargun.state;

import java.util.HashMap;
import java.util.Map;

/**
 * Support class for master and slave states.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class StateBase {

   private Map<Object, Object> stateMap = new HashMap<Object, Object>();
   private String configName;
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

   public void setClusterSize(int clusterSize) {
      this.clusterSize = clusterSize;
   }

   public int getMaxClusterSize() {
      return maxClusterSize;
   }

   public void setMaxClusterSize(int maxClusterSize) {
      this.maxClusterSize = maxClusterSize;
   }

   public void remove(Object key) {
      stateMap.remove(key);
   }

   public void put(Object key, Object value) {
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
}
