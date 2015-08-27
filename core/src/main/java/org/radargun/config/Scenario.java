package org.radargun.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.radargun.Stage;
import org.radargun.reporting.Report;
import org.radargun.state.StateBase;
import org.radargun.utils.MapEntry;
import org.radargun.utils.Utils;

/**
 * List of stages in order in which these should be executed.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Scenario implements Serializable {
   private List<StageDescription> stages = new ArrayList<>();
   private Map<String, Integer> labels = new HashMap<>();

   public List<StageDescription> getStages() {
      return stages;
   }

   /**
    * @param stageClass
    * @param properties Stage's attributes as written in configuration - evaluation takes place on slave.
    * @param labelName  Label that should be used together with stage-defined prefix and suffix to uniquely
    *                   identify this stage in the scenario. Can be null.
    */
   public void addStage(Class<? extends Stage> stageClass, Map<String, Definition> properties, String labelName) {
      org.radargun.config.Stage annotation = stageClass.getAnnotation(org.radargun.config.Stage.class);
      if (annotation == null) {
         throw new IllegalArgumentException("Class " + stageClass.getName() + " does not have annotation @Stage!");
      }
      Label label = annotation.label();
      String fullLabelName = Utils.concat(label.separator(), label.prefix(), labelName, label.suffix());
      if (!fullLabelName.isEmpty()) {
         if (labels.put(fullLabelName, stages.size()) != null) {
            throw new IllegalArgumentException("Label '" + label + "' was already defined (hint: two loops without name?)");
         }
      }
      stages.add(new StageDescription(stageClass, properties));
   }

   /**
    * Get instance of stage with given ID, using additional properties (evaluable as ${foo}) from localExtras.
    *
    * @param stageId     ID of the executed stage.
    * @param state       Master's or slave's state - used to resolve the properties.
    * @param localExtras Additional properties that could be used for property value resolution.
    * @param report      Report where the stage execution should be recorded.
    * @return Instance of the stage with properties set (not initialized and not injected with traits yet).
    */
   public org.radargun.Stage getStage(int stageId, StateBase state, Map<String, String> localExtras, Report report) {
      StageDescription description = stages.get(stageId);
      org.radargun.Stage stage;
      try {
         stage = description.stageClass.newInstance();
      } catch (Exception e) {
         throw new RuntimeException("Cannot instantiate " + description.stageClass.getName(), e);
      }
      PropertyHelper.setPropertiesFromDefinitions(stage, description.properties,
              localExtras, state != null ? state.asStringMap() : Collections.EMPTY_MAP);

      if (report != null) {
         Report.Stage reportStage = report.addStage(stage.getName());
         recordStage(reportStage, stage, description);
      }
      return stage;
   }

   private void recordStage(Report.Stage reportStage, org.radargun.Stage stage, StageDescription description) {
      // expand complex definitions
      Queue<Map.Entry<String, Definition>> queue = new LinkedList<>(description.properties.entrySet());
      HashMap<String, Definition> expanded = new HashMap<>();
      Map.Entry<String, Definition> entry;
      while ((entry = queue.poll()) != null) {
         if (entry.getValue() instanceof SimpleDefinition) {
            expanded.put(entry.getKey(), entry.getValue());
         } else if (entry.getValue() instanceof ComplexDefinition) {
            for (ComplexDefinition.Entry subentry : ((ComplexDefinition) entry.getValue()).getAttributes()) {
               queue.add(new MapEntry<>(entry.getKey() + subentry.name, subentry.definition));
            }
            expanded.put(entry.getKey(), entry.getValue());
         } else throw new IllegalArgumentException("Unknown definition type: " + entry.getValue());
      }

      for (Map.Entry<String, Path> property : PropertyHelper.getProperties(description.stageClass, true, false, false).entrySet()) {
         String propertyName = property.getKey();
         Path path = property.getValue();
         Definition definition = expanded.get(propertyName);
         reportStage.addProperty(propertyName, definition, PropertyHelper.getPropertyString(path, stage));
      }
   }

   /**
    * @return Total number of stages in the scenario. All stage IDs should be < this number.
    */
   public int getStageCount() {
      return stages.size();
   }

   /**
    * @param label Full label of the stage.
    * @return Stage ID of stage with given label.
    */
   public int getLabel(String label) {
      Integer id = labels.get(label);
      return id == null ? -1 : id;
   }

   public static class StageDescription implements Serializable {
      Class<? extends org.radargun.Stage> stageClass;
      /* Common properties as specified in configuraion */
      Map<String, Definition> properties;

      private StageDescription(Class<? extends org.radargun.Stage> stageClass, Map<String, Definition> properties) {
         this.stageClass = stageClass;
         this.properties = properties;
      }
   }
}
