package org.radargun.config;

import org.radargun.Version;
import org.w3c.dom.Element;

/**
 * Generates XSD file describing RadarGun 3.0 scenario configuration.
 * This file is expected to be run from command-line, or rather
 * build script.
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class ScenarioSchemaGenerator extends SchemaGenerator implements ConfigSchema {
   protected static final String NAMESPACE = "urn:radargun:scenario:" + Version.SCHEMA_VERSION;
   private static final String TYPE_REPEAT = "repeat";

   public ScenarioSchemaGenerator() {
      super(null, NAMESPACE, "radargun-");
   }

   @Override
   protected String findDocumentation(Class<?> clazz) {
      return null;
   }

   /**
    * Generates scenario scheme file
    */
   @Override
   protected void generate() {
      createSchemaElement(NAMESPACE);

      createComplexElement(schema, ELEMENT_SCENARIO, null, null, THIS_PREFIX + TYPE_STAGES, null);

      intType = generateSimpleType(int.class, DefaultConverter.class);

      generateStagesType();
   }

   void generateStagesType() {

      Element stagesType = createComplexType(TYPE_STAGES, null, true, true, null);
      Element stagesChoice = createChoice(createSequence(stagesType), 1, -1);

      Element repeatType = createComplexType(TYPE_REPEAT, THIS_PREFIX + TYPE_STAGES, true, false, null);
      addAttribute(repeatType, ATTR_TIMES, intType.toString(), null, false);
      addAttribute(repeatType, ATTR_FROM, intType.toString(), null, false);
      addAttribute(repeatType, ATTR_TO, intType.toString(), null, false);
      addAttribute(repeatType, ATTR_INC, intType.toString(), null, false);
      addAttribute(repeatType, ATTR_NAME, false);
      createReference(stagesChoice, ELEMENT_REPEAT, THIS_PREFIX + TYPE_REPEAT);

      createAny(stagesChoice, 1, 1, XS_OTHER_NAMESPACE);
   }

   /**
    * Generate the XSD file for scenario. First argument is directory where the XSD file should be placed
    * (it will be named radargunScenario-{version}.xsd.
    */
   public static void main(String[] args) {
      if (args.length < 1 || args[0] == null)
         throw new IllegalArgumentException("No schema location directory specified!");
      new ScenarioSchemaGenerator().generate(args[0], String.format("radargun-scenario-%s.xsd", Version.SCHEMA_VERSION));
   }
}
