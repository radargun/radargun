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
   private Map<String, Object> persistentMap = new HashMap<>();
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

   public void remove(Object key) {
      stateMap.remove(key);
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

   /**
    * Write an entry to the state that will persist after service destruction
    * (the value can be reused in next configurations).
    *
    * @param key
    * @param value
    */
   public void putPersistent(String key, Object value) {
      if (hasServiceClassLoader(value)) {
         throw new IllegalArgumentException("Class " + value.getClass() + " was loaded by "
               + value.getClass().getClassLoader() + " - this could cause a leak!");
      }
      persistentMap.put(key, value);
   }

   private boolean hasServiceClassLoader(Object value) {
      if (value == null) {
         return false;
      }
      ClassLoader valueClassLoader = value.getClass().getClassLoader();
      if (valueClassLoader == null) {
         return false; // primitive type
      }
      ClassLoader currentClassLoader = getClass().getClassLoader();
      while (currentClassLoader != null) {
         if (valueClassLoader == currentClassLoader) {
            return false;
         }
         currentClassLoader = currentClassLoader.getParent();
      }
      return true;
   }

   /**
    * Get an entry that was stored using {@link #putPersistent(String, Object)}.
    * @param key
    * @return
    */
   public Object getPersistent(String key) {
      return persistentMap.get(key);
   }

   /**
    * Remove an entry that was stored using {@link #putPersistent(String, Object)}.
    * @param key
    * @return
    */
   public Object removePersistent(String key) {
      return persistentMap.remove(key);
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
