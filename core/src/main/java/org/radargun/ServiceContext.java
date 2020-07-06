package org.radargun;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ServiceContext implements Serializable {
   private String currentGroup;
   private String currentPlugin;
   private int currentWorkerIndex;
   private Map<String, Object> currentProperties = new HashMap<>();

   public ServiceContext(String group, String plugin, int workerIndex) {
      currentGroup = group;
      currentPlugin = plugin;
      currentWorkerIndex = workerIndex;
   }

   public String getPrefix() {
      return currentGroup + "." + currentWorkerIndex;
   }

   public String getGroup() {
      return currentGroup;
   }

   public String getPlugin() {
      return currentPlugin;
   }

   public int getWorkerIndex() {
      return currentWorkerIndex;
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
