package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * List of stages in order in which these should be executed.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Scenario implements Serializable {

   List<StageDescription> stages = new ArrayList<StageDescription>();

   /**
    * @param stageClass
    * @param properties Stage's attributes as written in configuration - evaluation takes place on slave
    * @param extras Additional properties (evaluable as ${foo}) specified for this stage
    */
   public void addStage(Class<? extends org.radargun.Stage> stageClass, Map<String, Definition> properties, Map<String, String> extras) {
      stages.add(new StageDescription(stageClass, properties, extras));
   }

   /**
    * Get instance of stage with given ID, using additional properties (evaluable as ${foo}) from localExtras
    * @param stageId
    * @param localExtras
    * @return
    */
   public org.radargun.Stage getStage(int stageId, Map<String, String> localExtras) {
      StageDescription description = stages.get(stageId);
      org.radargun.Stage stage;
      try {
         stage = description.stageClass.newInstance();
      } catch (Exception e) {
         throw new RuntimeException("Cannot instantiate " + description.stageClass.getName(), e);
      }
      Map<String, String> backups = new HashMap<String, String>();
      for (Map.Entry<String, String> extra : description.extras.entrySet()) {
         backups.put(extra.getKey(), System.getProperty(extra.getKey()));
         System.setProperty(extra.getKey(), extra.getValue());
      }
      backupProperties(description.extras.keySet(), backups);
      backupProperties(localExtras.keySet(), backups);
      setProperties(description.extras);
      setProperties(localExtras);
      PropertyHelper.setPropertiesFromDefinitions(stage, description.properties, false, true);
      setProperties(backups);
      return stage;
   }

   /**
    * Retrieve the original definition of Stage's attributes as appeared in config. No evaluation.
    *
    * @param stageId
    * @return
    */
   public Map<String, Definition> getPropertiesDefinitions(int stageId) {
      return stages.get(stageId).properties;
   }

   private void setProperties(Map<String, String> properties) {
      for (Map.Entry<String, String> property : properties.entrySet()) {
         System.setProperty(property.getKey(), property.getValue() == null ? "" : property.getValue());
      }
   }

   private void backupProperties(Set<String> properties, Map<String, String> backups) {
      for (String property : properties) {
         backups.put(property, System.getProperty(property));
      }
   }

   /**
    * Total amount of stages in the scenario. All stage IDs should be < this number.
    * @return
    */
   public int getStageCount() {
      return stages.size();
   }

   private static class StageDescription implements Serializable {
      Class<? extends org.radargun.Stage> stageClass;
      /* Common properties as specified in configuraion */
      Map<String, Definition> properties;
      /* Repeat counters, slave num counters...*/
      Map<String, String> extras;

      private StageDescription(Class<? extends org.radargun.Stage> stageClass, Map<String, Definition> properties, Map<String, String> extras) {
         this.stageClass = stageClass;
         this.properties = properties;
         this.extras = extras;
      }
   }
}
