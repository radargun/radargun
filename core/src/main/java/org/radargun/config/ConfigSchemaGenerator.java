package org.radargun.config;

import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.stages.ScenarioDestroyStage;
import org.radargun.stages.ScenarioInitStage;
import org.w3c.dom.Element;

/**
 * Generates XSD file describing RadarGun 3.0 configuration.
 * <p/>
 * There are basically two parts: hand-coded stable configuration
 * (such as cluster & configuration definitions), and stage lists
 * with properties, converters etc. When stages are added/removed
 * or properties change, the XSD file is automatically updated to
 * reflect this.
 * <p/>
 * This file is expected to be run from command-line, or rather
 * build script.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ConfigSchemaGenerator extends SchemaGenerator implements ConfigSchema {
   public static final String VERSION = "3.0"; // TODO: read version from plugin
   private static final String TYPE_CLUSTER_BASE = "cluster_base";
   private static final String TYPE_CLUSTER = "cluster";
   private static final String TYPE_PROPERTY = "property";

   /**
    * Generates benchmark scheme file
    */
   @Override
   protected void generate() {
      Element schema = createSchemaElement("radargun:benchmark:" + VERSION);
      addInclude(schema, String.format("radargunScenario-%s.xsd", VERSION));

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
      Element setupSequence = createSequence(setupComplex);
      String vmArgsType = generateClass(VmArgs.class);
      createReference(setupSequence, ELEMENT_VM_ARGS, vmArgsType, 0, 1);
      createAny(setupSequence);
      addAttribute(configComplex, ATTR_NAME, true);
      addAttribute(setupComplex, ATTR_PLUGIN, true);
      addAttribute(setupComplex, ATTR_GROUP, false);

      createReference(benchmarkSequence, ELEMENT_INIT, RG_PREFIX + class2xmlId(ScenarioInitStage.class), 0, 1);
      createReference(benchmarkSequence, ELEMENT_SCENARIO, RG_PREFIX + ELEMENT_SCENARIO_COMPLEX);
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

   @Override
   protected String findDocumentation(Class<?> clazz) {
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage) clazz.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation != null) return stageAnnotation.doc();
      return null;
   }

   /**
    * Adds include tag to the element
    *
    * @param element  to which include is added
    * @param location of include file e.g. scenario.xsd
    * @return modified element
    */
   protected Element addInclude(Element element, String location) {
      Element schema = doc.createElementNS(NS_XS, RG_PREFIX + XS_INCLUDE);
      schema.setAttribute(XS_SCHEMA_LOCATION, location);
      element.appendChild(schema);
      return schema;
   }

   /**
    * Generate the XSD file. First argument is directory where the XSD file should be placed
    * (it will be named radargun-{version}.xsd.
    */
   public static void main(String[] args) {
      if (args.length < 1 || args[0] == null)
         throw new IllegalArgumentException("No schema location directory specified!");

      new ConfigSchemaGenerator().generate(args[0], String.format("radargun-%s.xsd", VERSION));
   }
}

