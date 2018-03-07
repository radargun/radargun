package org.radargun;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ServiceContext implements Serializable {
   private String currentGroup;
   private String currentPlugin;
   private int currentSlaveIndex;
   private Map<String, Object> currentProperties = new HashMap<>();

   public ServiceContext(String group, String plugin, int slaveIndex) {
      currentGroup = group;
      currentPlugin = plugin;
      currentSlaveIndex = slaveIndex;
   }

   public String getPrefix() {
      return currentGroup + "." + currentSlaveIndex;
   }

   public String getGroup() {
      return currentGroup;
   }

   public String getPlugin() {
      return currentPlugin;
   }

   public int getSlaveIndex() {
      return currentSlaveIndex;
   }

   public void setProperties(Map<String, Object> props) {
      currentProperties = props;
   }

   public Map<String, Object> getProperties() {
      return currentProperties;
   }

   public void addProperties(Map<String, Object> fromMap) {
      currentProperties.putAll(fromMap);
   }
}
