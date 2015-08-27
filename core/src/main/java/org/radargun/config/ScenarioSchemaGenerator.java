package org.radargun.config;

import org.radargun.Stage;
import org.w3c.dom.Element;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Generates XSD file describing RadarGun 3.0 scenario configuration.
 * This file is expected to be run from command-line, or rather
 * build script.
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class ScenarioSchemaGenerator extends SchemaGenerator implements ConfigSchema {
   private static final String TYPE_REPEAT = "repeat";
   private static final String TYPE_STAGES = "stages";
   private static final String VERSION = ConfigSchemaGenerator.VERSION;

   private static Map<Class<? extends org.radargun.Stage>, String> stages = new TreeMap<>(new Comparator<Class<? extends org.radargun.Stage>>() {
      @Override
      public int compare(Class<? extends org.radargun.Stage> o1, Class<? extends Stage> o2) {
         int c = o1.getSimpleName().compareTo(o2.getSimpleName());
         return c != 0 ? c : o1.getCanonicalName().compareTo(o2.getCanonicalName());
      }
   });

   @Override
   protected String findDocumentation(Class<?> clazz) {
      org.radargun.config.Stage stageAnnotation = clazz.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation != null) return stageAnnotation.doc();
      return null;
   }

   /**
    * Generates scenario scheme file
    */
   @Override
   protected void generate() {
      Element schema = createSchemaElement("radargun-scenario:benchmark:" + VERSION);

      intType = generateSimpleType(int.class, DefaultConverter.class);

      Element scenarioElement = doc.createElementNS(NS_XS, XS_ELEMENT);
      scenarioElement.setAttribute(XS_NAME, ELEMENT_SCENARIO);
      schema.appendChild(scenarioElement);

      Element scenarioComplex = createComplexType(schema, ELEMENT_SCENARIO_COMPLEX, RG_PREFIX + TYPE_STAGES, true, false, null);
      createReference(schema, ELEMENT_SCENARIO_COMPLEX, RG_PREFIX + ELEMENT_SCENARIO_COMPLEX);

      addAttribute(scenarioComplex, ATTR_URL, false);
      scenarioElement.setAttribute(XS_TYPE, RG_PREFIX + ELEMENT_SCENARIO_COMPLEX);

      Element stagesType = createComplexType(schema, TYPE_STAGES, null, true, true, null);
      Element stagesChoice = createChoice(createSequence(stagesType), 1, -1);

      Element repeatType = createComplexType(schema, TYPE_REPEAT, RG_PREFIX + TYPE_STAGES, true, false, null);
      addAttribute(repeatType, ATTR_TIMES, intType, null, false);
      addAttribute(repeatType, ATTR_FROM, intType, null, false);
      addAttribute(repeatType, ATTR_TO, intType, null, false);
      addAttribute(repeatType, ATTR_INC, intType, null, false);
      addAttribute(repeatType, ATTR_NAME, false);
      createReference(stagesChoice, ELEMENT_REPEAT, RG_PREFIX + TYPE_REPEAT);

      generateStageDefinitions(new Element[]{stagesChoice});

   }

   private void generateStageDefinitions(Element[] parents) {
      Set<Class<? extends Stage>> generatedStages = new HashSet<>();
      for (Map.Entry<Class<? extends Stage>, String> entry : stages.entrySet()) {
         generateStage(parents, entry.getKey(), generatedStages);
      }
   }

   private void generateStage(Element[] parents, Class stage, Set<Class<? extends Stage>> generatedStages) {
      if (generatedStages.contains(stage)) return;
      boolean hasParentStage = Stage.class.isAssignableFrom(stage.getSuperclass());
      if (hasParentStage) {
         generateStage(parents, stage.getSuperclass(), generatedStages);
      }
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage) stage.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation == null) return; // not a proper stage

      String stageType = generateClass(stage);
      if (!Modifier.isAbstract(stage.getModifiers()) && !stageAnnotation.internal()) {
         for (Element parent : parents) {
            createReference(parent, XmlHelper.camelCaseToDash(StageHelper.getStageName(stage)), stageType);
         }
         if (!stageAnnotation.deprecatedName().equals(org.radargun.config.Stage.NO_DEPRECATED_NAME)) {
            for (Element parent : parents) {
               createReference(parent, XmlHelper.camelCaseToDash(stageAnnotation.deprecatedName()), stageType);
            }
         }
      }
      generatedStages.add(stage);
   }

   /**
    * Generate the XSD file for scenario. First argument is directory where the XSD file should be placed
    * (it will be named radargunScenario-{version}.xsd.
    */
   public static void main(String[] args) {
      if (args.length < 1 || args[0] == null)
         throw new IllegalArgumentException("No schema location directory specified!");
      System.out.println("ConfigSchemaGenerator argument" + args[0]);
      for (Class<? extends org.radargun.Stage> stage : StageHelper.getStages().values()) {
         stages.put(stage, stage.getProtectionDomain().getCodeSource().getLocation().getPath());
      }
      new ScenarioSchemaGenerator().generate(args[0], String.format("radargunScenario-%s.xsd", VERSION));

   }
}
