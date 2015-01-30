package org.radargun.config;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.radargun.Stage;
import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.stages.ScenarioDestroyStage;
import org.radargun.stages.ScenarioInitStage;
import org.w3c.dom.Element;

/**
 * Generates XSD file describing RadarGun 2.0 configuration.
 *
 * There are basically two parts: hand-coded stable configuration
 * (such as cluster & configuration definitions), and stage lists
 * with properties, converters etc. When stages are added/removed
 * or properties change, the XSD file is automatically updated to
 * reflect this.
 *
 * This file is expected to be run from command-line, or rather
 * build script.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ConfigSchemaGenerator extends SchemaGenerator implements ConfigSchema {
   private static final String VERSION = "2.0"; // TODO: read version from plugin
   private static final String TYPE_CLUSTER_BASE = "cluster_base";
   private static final String TYPE_CLUSTER = "cluster";
   private static final String TYPE_PROPERTY = "property";
   private static final String TYPE_REPEAT = "repeat";
   private static final String TYPE_SCENARIO = "scenario";
   private static final String TYPE_STAGES = "stages";

   /* Stages classes sorted by name with its source jar */
   private static Map<Class<? extends Stage>, String> stages = new TreeMap<Class<? extends Stage>, String>(new Comparator<Class<? extends Stage>>() {
      @Override
      public int compare(Class<? extends Stage> o1, Class<? extends Stage> o2) {
         int c = o1.getSimpleName().compareTo(o2.getSimpleName());
         return c != 0 ? c : o1.getCanonicalName().compareTo(o2.getCanonicalName());
      }
   });

   @Override
   protected void generate() {
      Element schema = createSchemaElement("radargun:benchmark:" + VERSION);

      intType = generateSimpleType(int.class, DefaultConverter.class);

      Element benchmarkElement = doc.createElementNS(NS_XS, XS_ELEMENT);
      benchmarkElement.setAttribute(XS_NAME, ELEMENT_BENCHMARK);
      schema.appendChild(benchmarkElement);
      Element benchmarkComplex = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      benchmarkElement.appendChild(benchmarkComplex);
      Element benchmarkSequence = createSequence(benchmarkComplex);

      Element masterComplex = createComplexElement(benchmarkSequence, ELEMENT_MASTER, 0, 1);
      addAttribute(masterComplex, ATTR_BIND_ADDRESS, false);
      addAttribute(masterComplex, ATTR_PORT, intType, null, false);

      Element clustersChoice = createChoice(benchmarkSequence, 0, 1);
      Element localComplex = createComplexElement(clustersChoice, ELEMENT_LOCAL, 0, 1);
      Element clustersComplex = createComplexElement(clustersChoice, ELEMENT_CLUSTERS, 0, 1);
      Element clusterChoice = createChoice(clustersComplex, 1, -1);
      Element baseClusterType = createComplexType(schema, TYPE_CLUSTER_BASE, null, true, false, null);
      Element groupComplex = createComplexElement(createSequence(baseClusterType), ELEMENT_GROUP, 0, -1);
      Element sizedClusterType = createComplexType(schema, TYPE_CLUSTER, RG_PREFIX + TYPE_CLUSTER_BASE, true, false, null);
      Element scaleElement = createComplexElement(clusterChoice, ELEMENT_SCALE, 0, -1);
      createReference(clusterChoice, ELEMENT_CLUSTER, RG_PREFIX + TYPE_CLUSTER);
      createReference(createSequence(scaleElement), ELEMENT_CLUSTER, RG_PREFIX + TYPE_CLUSTER_BASE);
      addAttribute(groupComplex, ATTR_NAME, true);
      addAttribute(groupComplex, ATTR_SIZE, intType, null, true);
      addAttribute(sizedClusterType, ATTR_SIZE, intType, null, false);
      addAttribute(scaleElement, ATTR_FROM, intType, null, true);
      addAttribute(scaleElement, ATTR_TO, intType, null, true);
      addAttribute(scaleElement, ATTR_INC, intType, null, false);

      Element propertyType = createComplexType(schema, TYPE_PROPERTY, "string", false, false, null);
      addAttribute(propertyType, ATTR_NAME, true);

      Element configurationsComplex = createComplexElement(benchmarkSequence, ELEMENT_CONFIGURATIONS, 1, 1);
      Element configComplex = createComplexElement(createSequence(configurationsComplex), ELEMENT_CONFIG, 1, -1);
      Element setupComplex = createComplexElement(createSequence(configComplex), ELEMENT_SETUP, 1, -1);
      createAny(createSequence(setupComplex));
      addAttribute(configComplex, ATTR_NAME, true);
      addAttribute(setupComplex, ATTR_PLUGIN, true);
      addAttribute(setupComplex, ATTR_GROUP, false);

      createReference(benchmarkSequence, ELEMENT_INIT, RG_PREFIX + class2xmlId(ScenarioInitStage.class), 0, 1);

      Element stagesType = createComplexType(schema, TYPE_STAGES, null, true, true, null);
      Element stagesChoice = createChoice(createSequence(stagesType), 1, -1);
      Element scenarioType = createComplexType(schema, ELEMENT_SCENARIO, RG_PREFIX + TYPE_STAGES, true, false, null);
      createReference(benchmarkSequence, ELEMENT_SCENARIO, RG_PREFIX + TYPE_SCENARIO);

      Element repeatType = createComplexType(schema, TYPE_REPEAT, RG_PREFIX + TYPE_STAGES, true, false, null);
      addAttribute(repeatType, ATTR_TIMES, intType, null, false);
      addAttribute(repeatType, ATTR_FROM, intType, null, false);
      addAttribute(repeatType, ATTR_TO, intType, null, false);
      addAttribute(repeatType, ATTR_INC, intType, null, false);
      addAttribute(repeatType, ATTR_NAME, false);
      createReference(stagesChoice, ELEMENT_REPEAT, RG_PREFIX + TYPE_REPEAT);

      generateStageDefinitions(new Element[]{ stagesChoice });

      createReference(benchmarkSequence, ELEMENT_DESTROY, RG_PREFIX + class2xmlId(ScenarioDestroyStage.class), 0, 1);
      createReference(benchmarkSequence, ELEMENT_CLEANUP, RG_PREFIX + class2xmlId(ScenarioCleanupStage.class), 0, 1);

      Element reportsComplex = createComplexElement(benchmarkSequence, ELEMENT_REPORTS, 0, 1);
      Element reporterComplex = createComplexElement(createSequence(reportsComplex), ELEMENT_REPORTER, 1, -1);
      Element reporterSequence = createSequence(reporterComplex);
      createAny(reporterSequence);

      Element reportComplex = createComplexElement(reporterSequence, ELEMENT_REPORT, 0, -1);
      Element reportSequence = createSequence(reportComplex);
      createAny(reportSequence);

      addAttribute(reporterComplex, ATTR_TYPE, true);
   }

   private void generateStageDefinitions(Element[] parents) {
      Set<Class<? extends Stage>> generatedStages = new HashSet<Class<? extends Stage>>();
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
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage)stage.getAnnotation(org.radargun.config.Stage.class);
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

   @Override
   protected String findDocumentation(Class<?> clazz) {
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage)clazz.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation != null) return stageAnnotation.doc();
      return null;
   }

   /**
    * Generate the XSD file. First argument is directory where the XSD file should be placed
    * (it will be named radargun-{version}.xsd.
    */
   public static void main(String[] args) {
      if (args.length < 1 || args[0] == null)
         throw new IllegalArgumentException("No schema location directory specified!");

      for (Class<? extends Stage> stage : StageHelper.getStages().values()) {
         stages.put(stage, stage.getProtectionDomain().getCodeSource().getLocation().getPath());
      }
      new ConfigSchemaGenerator().generate(args[0], String.format("radargun-%s.xsd", VERSION));
   }
}

