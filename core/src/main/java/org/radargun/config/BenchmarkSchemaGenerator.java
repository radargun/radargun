package org.radargun.config;

import org.radargun.Version;
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
public class BenchmarkSchemaGenerator extends SchemaGenerator implements ConfigSchema {
   protected static final String NAMESPACE = "urn:radargun:benchmark:" + Version.SCHEMA_VERSION;
   protected static final String CORE_NAMESPACE = StageHelper.NAMESPACE_ROOT + "core:" + Version.SCHEMA_VERSION;
   private static final String CORE_PREFIX = "core:";
   private static final String TYPE_CLUSTER_BASE = "cluster_base";
   private static final String TYPE_CLUSTER = "cluster";
   private static final String TYPE_PROPERTY = "property";
   private static final String TYPE_INIT = "init";
   private static final String TYPE_DESTROY = "destroy";
   private static final String TYPE_CLEANUP = "cleanup";

   public BenchmarkSchemaGenerator() {
      super(null, NAMESPACE, "radargun-");
   }

   /**
    * Generates benchmark scheme file
    */
   @Override
   protected void generate() {
      createSchemaElement(NAMESPACE);
      addImport(CORE_NAMESPACE, "radargun-core-" + Version.SCHEMA_VERSION + ".xsd", "core");

      intType = generateSimpleType(int.class, DefaultConverter.class);

      Element benchmarkElement = doc.createElementNS(NS_XS, XS_ELEMENT);
      benchmarkElement.setAttribute(XS_NAME, ELEMENT_BENCHMARK);
      schema.appendChild(benchmarkElement);

      Element benchmarkComplex = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      benchmarkElement.appendChild(benchmarkComplex);
      Element benchmarkSequence = createSequence(benchmarkComplex);

      Element masterComplex = createComplexElement(benchmarkSequence, ELEMENT_MASTER, 0, 1, null);
      addAttribute(masterComplex, ATTR_BIND_ADDRESS, false);
      addAttribute(masterComplex, ATTR_PORT, intType.toString(), null, false);

      Element clustersChoice = createChoice(benchmarkSequence, 0, 1);
      Element clustersComplex = createComplexElement(clustersChoice, ELEMENT_CLUSTERS, 0, 1, null);
      Element clusterChoice = createChoice(clustersComplex, 1, -1);
      Element baseClusterType = createComplexType(TYPE_CLUSTER_BASE, null, true, false, null);
      Element groupComplex = createComplexElement(createSequence(baseClusterType), ELEMENT_GROUP, 0, -1, null);
      Element sizedClusterType = createComplexType(TYPE_CLUSTER, THIS_PREFIX + TYPE_CLUSTER_BASE, true, false, null);
      Element scaleElement = createComplexElement(clusterChoice, ELEMENT_SCALE, 0, -1, null);
      createReference(clusterChoice, ELEMENT_CLUSTER, THIS_PREFIX + TYPE_CLUSTER);
      createReference(createSequence(scaleElement), ELEMENT_CLUSTER, THIS_PREFIX + TYPE_CLUSTER_BASE);
      addAttribute(groupComplex, ATTR_NAME, true);
      addAttribute(groupComplex, ATTR_SIZE, intType.toString(), null, true);
      addAttribute(sizedClusterType, ATTR_SIZE, intType.toString(), null, false);
      addAttribute(scaleElement, ATTR_FROM, intType.toString(), null, true);
      addAttribute(scaleElement, ATTR_TO, intType.toString(), null, true);
      addAttribute(scaleElement, ATTR_INC, intType.toString(), null, false);

      Element propertyType = createComplexType(TYPE_PROPERTY, "string", false, false, null);
      addAttribute(propertyType, ATTR_NAME, true);

      Element configurationsComplex = createComplexElement(benchmarkSequence, ELEMENT_CONFIGURATIONS, 1, 1, null);
      Element configComplex = createComplexElement(createSequence(configurationsComplex), ELEMENT_CONFIG, 1, -1, null);
      Element setupComplex = createComplexElement(createSequence(configComplex), ELEMENT_SETUP, 1, -1, null);
      Element setupSequence = createSequence(setupComplex);
      Element env = createComplexElement(setupSequence, ELEMENT_ENVIRONMENT, 0, 1, "Environment variables.");
      Element envSequence = createSequence(env);
      Element var = createComplexElement(envSequence, ELEMENT_VAR, 1, 1, "Environment variable definition.");
      addAttribute(var, ATTR_NAME, true);
      addAttribute(var, ATTR_VALUE, true);

      XmlType vmArgsType = generateClass(VmArgs.class);
      createReference(setupSequence, ELEMENT_VM_ARGS, vmArgsType.toString(), 0, 1);
      createAny(setupSequence);
      addAttribute(configComplex, ATTR_NAME, true);
      addAttribute(setupComplex, ATTR_PLUGIN, true);
      addAttribute(setupComplex, ATTR_GROUP, false);

      createComplexType(TYPE_INIT, CORE_PREFIX + class2xmlId(ScenarioInitStage.class), true, false, null);
      createReference(benchmarkSequence, ELEMENT_INIT, THIS_PREFIX + TYPE_INIT, 0, 1);

      ScenarioSchemaGenerator scenarioSchemaGenerator = new ScenarioSchemaGenerator();
      scenarioSchemaGenerator.setDocSchema(doc, schema);
      scenarioSchemaGenerator.intType = intType;
      scenarioSchemaGenerator.generateStagesType(0);
      Element scenarioType = createComplexType(TYPE_SCENARIO, THIS_PREFIX + TYPE_STAGES, true, false, null);
      addAttribute(scenarioType, ATTR_URL, false);
      createReference(benchmarkSequence, ELEMENT_SCENARIO, THIS_PREFIX + TYPE_SCENARIO);

      createComplexType(TYPE_DESTROY, CORE_PREFIX + class2xmlId(ScenarioDestroyStage.class), true, false, null);
      createReference(benchmarkSequence, ELEMENT_DESTROY, THIS_PREFIX + TYPE_DESTROY, 0, 1);

      createComplexType(TYPE_CLEANUP, CORE_PREFIX + class2xmlId(ScenarioCleanupStage.class), true, false, null);
      createReference(benchmarkSequence, ELEMENT_CLEANUP, THIS_PREFIX + TYPE_CLEANUP, 0, 1);

      Element reportsComplex = createComplexElement(benchmarkSequence, ELEMENT_REPORTS, 0, 1, null);
      Element reporterComplex = createComplexElement(createSequence(reportsComplex), ELEMENT_REPORTER, 1, -1, null);
      Element reporterSequence = createSequence(reporterComplex);
      createAny(reporterSequence);

      Element reportComplex = createComplexElement(reporterSequence, ELEMENT_REPORT, 0, -1, null);
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
    * Generate the XSD file. First argument is directory where the XSD file should be placed
    * (it will be named radargun-{version}.xsd.
    */
   public static void main(String[] args) {
      if (args.length < 1 || args[0] == null)
         throw new IllegalArgumentException("No schema location directory specified!");

      new BenchmarkSchemaGenerator().generate(args[0], String.format("radargun-benchmark-%s.xsd", Version.SCHEMA_VERSION));
   }
}

