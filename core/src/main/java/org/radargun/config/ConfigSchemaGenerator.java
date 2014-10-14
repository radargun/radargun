package org.radargun.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.radargun.Stage;
import org.radargun.stages.ScenarioCleanupStage;
import org.radargun.stages.ScenarioDestroyStage;
import org.radargun.stages.ScenarioInitStage;
import org.w3c.dom.Document;
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
public class ConfigSchemaGenerator implements ConfigSchema {
   private static final String NS_XS = "http://www.w3.org/2001/XMLSchema";
   private static final String RG_PREFIX = "rg:";
   private static final String XS_ELEMENT = "element";
   private static final String XS_RESTRICTION = "restriction";
   private static final String XS_PATTERN = "pattern";
   private static final String XS_ENUMERATION = "enumeration";
   private static final String XS_SIMPLE_TYPE = "simpleType";
   private static final String XS_COMPLEX_TYPE = "complexType";
   private static final String XS_SEQUENCE = "sequence";
   private static final String XS_UNION = "union";
   private static final String XS_MIN_OCCURS = "minOccurs";
   private static final String XS_MAX_OCCURS = "maxOccurs";
   private static final String XS_ATTRIBUTE = "attribute";
   private static final String XS_NAME = "name";
   private static final String XS_TYPE = "type";
   private static final String XS_USE = "use";
   private static final String XS_REQUIRED = "required";
   private static final String XS_STRING = "string";
   private static final String XS_INTEGER = "integer";
   private static final String XS_LONG = "long";
   private static final String XS_FLOAT = "float";
   private static final String XS_DOUBLE = "double";
   private static final String XS_BOOLEAN = "boolean";
   private static final String XS_COMPLEX_CONTENT = "complexContent";
   private static final String XS_SIMPLE_CONTENT = "simpleContent";
   private static final String XS_EXTENSION = "extension";
   private static final String XS_BASE = "base";
   private static final String XS_VALUE = "value";
   private static final String XS_ABSTRACT = "abstract";
   private static final String XS_CHOICE = "choice";
   private static final String XS_ANNOTATION = "annotation";
   private static final String XS_DOCUMENTATION = "documentation";
   private static final String TYPE_CLUSTER_BASE = "cluster_base";
   private static final String TYPE_CLUSTER = "cluster";
   private static final String TYPE_PROPERTY = "property";
   private static final String TYPE_REPEAT = "repeat";
   private static final String TYPE_SCENARIO = "scenario";
   private static final String TYPE_STAGES = "stages";

   private static Set<String> generatedTypes = new HashSet<String>();
   /* Stages classes sorted by name with its source jar */
   private static Map<Class<? extends Stage>, String> stages = new TreeMap<Class<? extends Stage>, String>(new Comparator<Class<? extends Stage>>() {
      @Override
      public int compare(Class<? extends Stage> o1, Class<? extends Stage> o2) {
         int c = o1.getSimpleName().compareTo(o2.getSimpleName());
         return c != 0 ? c : o1.getCanonicalName().compareTo(o2.getCanonicalName());
      }
   });
   private static String intType;

   public static void generate(String directory) {
      try {
         PrintWriter writer = new PrintWriter(new File(directory + File.separator + "radargun-2.0.xsd"));
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         DocumentBuilder builder = factory.newDocumentBuilder();
         Document doc = builder.newDocument();
         doc.setXmlVersion("1.0");
         doc.setXmlStandalone(true);
         generate(doc);

         TransformerFactory tf = TransformerFactory.newInstance();
         tf.setAttribute("indent-number", 3);
         Transformer trans = tf.newTransformer();
         trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
         trans.setOutputProperty(OutputKeys.INDENT, "yes");


         StreamResult result = new StreamResult(writer);
         DOMSource source = new DOMSource(doc);
         trans.transform(source, result);
         writer.flush();
      } catch (FileNotFoundException e) {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      } catch (ParserConfigurationException e) {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      } catch (TransformerConfigurationException e) {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      } catch (TransformerException e) {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
   }

   private static void generate(Document doc) {
      Element schema = doc.createElementNS(NS_XS, "schema");
      schema.setAttribute("attributeFormDefault", "unqualified");
      schema.setAttribute("elementFormDefault", "qualified");
      schema.setAttribute("version", "1.0");
      schema.setAttribute("targetNamespace", "urn:radargun:benchmark:2.0");
      schema.setAttribute("xmlns:rg", "urn:radargun:benchmark:2.0");
      doc.appendChild(schema);

      intType = generateSimpleType(doc, schema, int.class, DefaultConverter.class);

      Element benchmarkElement = doc.createElementNS(NS_XS, XS_ELEMENT);
      benchmarkElement.setAttribute(XS_NAME, ELEMENT_BENCHMARK);
      schema.appendChild(benchmarkElement);
      Element benchmarkComplex = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      benchmarkElement.appendChild(benchmarkComplex);
      Element benchmarkSequence = createSequence(doc, benchmarkComplex);

      Element masterComplex = createComplexElement(doc, benchmarkSequence, ELEMENT_MASTER, 0, 1);
      addAttribute(doc, masterComplex, ATTR_BIND_ADDRESS, false);
      addAttribute(doc, masterComplex, ATTR_PORT, intType, null, false);

      Element clustersChoice = createChoice(doc, benchmarkSequence, 0, 1);
      Element localComplex = createComplexElement(doc, clustersChoice, ELEMENT_LOCAL, 0, 1);
      Element clustersComplex = createComplexElement(doc, clustersChoice, ELEMENT_CLUSTERS, 0, 1);
      Element clusterChoice = createChoice(doc, clustersComplex, 1, -1);
      Element baseClusterType = createComplexType(doc, schema, TYPE_CLUSTER_BASE, null, true, false, null);
      Element groupComplex = createComplexElement(doc, createSequence(doc, baseClusterType), ELEMENT_GROUP, 0, -1);
      Element sizedClusterType = createComplexType(doc, schema, TYPE_CLUSTER, RG_PREFIX + TYPE_CLUSTER_BASE, true, false, null);
      Element scaleElement = createComplexElement(doc, clusterChoice, ELEMENT_SCALE, 0, -1);
      createReference(doc, clusterChoice, ELEMENT_CLUSTER, RG_PREFIX + TYPE_CLUSTER);
      createReference(doc, createSequence(doc, scaleElement), ELEMENT_CLUSTER, RG_PREFIX + TYPE_CLUSTER_BASE);
      addAttribute(doc, groupComplex, ATTR_NAME, true);
      addAttribute(doc, groupComplex, ATTR_SIZE, intType, null, true);
      addAttribute(doc, sizedClusterType, ATTR_SIZE, intType, null, false);
      addAttribute(doc, scaleElement, ATTR_FROM, intType, null, true);
      addAttribute(doc, scaleElement, ATTR_TO, intType, null, true);
      addAttribute(doc, scaleElement, ATTR_INC, intType, null, false);

      Element propertyType = createComplexType(doc, schema, TYPE_PROPERTY, "string", false, false, null);
      addAttribute(doc, propertyType, ATTR_NAME, true);

      Element configurationsComplex = createComplexElement(doc, benchmarkSequence, ELEMENT_CONFIGURATIONS, 1, 1);
      Element configComplex = createComplexElement(doc, createSequence(doc, configurationsComplex), ELEMENT_CONFIG, 1, -1);
      Element setupComplex = createComplexElement(doc, createSequence(doc, configComplex), ELEMENT_SETUP, 1, -1);
      createReference(doc, createSequence(doc, setupComplex), ELEMENT_PROPERTY, RG_PREFIX + TYPE_PROPERTY, 0, -1);
      addAttribute(doc, configComplex, ATTR_NAME, true);
      addAttribute(doc, setupComplex, ATTR_PLUGIN, true);
      addAttribute(doc, setupComplex, ATTR_FILE, false);
      addAttribute(doc, setupComplex, ATTR_SERVICE, false);
      addAttribute(doc, setupComplex, ATTR_GROUP, false);

      createReference(doc, benchmarkSequence, ELEMENT_INIT, RG_PREFIX + class2xmlId(ScenarioInitStage.class), 0, 1);

      Element stagesType = createComplexType(doc, schema, TYPE_STAGES, null, true, true, null);
      Element stagesChoice = createChoice(doc, createSequence(doc, stagesType), 1, -1);
      Element scenarioType = createComplexType(doc, schema, ELEMENT_SCENARIO, RG_PREFIX + TYPE_STAGES, true, false, null);
      createReference(doc, benchmarkSequence, ELEMENT_SCENARIO, RG_PREFIX + TYPE_SCENARIO);

      Element repeatType = createComplexType(doc, schema, TYPE_REPEAT, RG_PREFIX + TYPE_STAGES, true, false, null);
      addAttribute(doc, repeatType, ATTR_TIMES, intType, null, false);
      addAttribute(doc, repeatType, ATTR_FROM, intType, null, false);
      addAttribute(doc, repeatType, ATTR_TO, intType, null, false);
      addAttribute(doc, repeatType, ATTR_INC, intType, null, false);
      addAttribute(doc, repeatType, ATTR_NAME, false);
      createReference(doc, stagesChoice, ELEMENT_REPEAT, RG_PREFIX + TYPE_REPEAT);

      generateStageDefinitions(doc, schema, new Element[]{ stagesChoice });

      createReference(doc, benchmarkSequence, ELEMENT_DESTROY, RG_PREFIX + class2xmlId(ScenarioDestroyStage.class), 0, 1);
      createReference(doc, benchmarkSequence, ELEMENT_CLEANUP, RG_PREFIX + class2xmlId(ScenarioCleanupStage.class), 0, 1);

      Element reportsComplex = createComplexElement(doc, benchmarkSequence, ELEMENT_REPORTS, 1, 1);
      Element reporterComplex = createComplexElement(doc, createSequence(doc, reportsComplex), ELEMENT_REPORTER, 1, -1);
      Element reporterSequence = createSequence(doc, reporterComplex);
      Element reportComplex = createComplexElement(doc, reporterSequence, ELEMENT_REPORT, 0, -1);
      Element propertiesComplex = createComplexElement(doc, reporterSequence, ELEMENT_PROPERTIES, 0, 1);
      createReference(doc, createSequence(doc, reportComplex), ELEMENT_PROPERTY, RG_PREFIX + TYPE_PROPERTY, 1, -1);
      createReference(doc, createSequence(doc, propertiesComplex), ELEMENT_PROPERTY, RG_PREFIX + TYPE_PROPERTY, 1, -1);
      addAttribute(doc, reporterComplex, ATTR_TYPE, true);
      String runType = generateSimpleType(doc, schema, ReporterConfiguration.RunCondition.class, DefaultConverter.class);
      addAttribute(doc, reporterComplex, ATTR_RUN, runType, null, false);
   }

   private static void generateStageDefinitions(Document doc, Element schema, Element[] parents) {
      Set<Class<? extends Stage>> generatedStages = new HashSet<Class<? extends Stage>>();
      for (Map.Entry<Class<? extends Stage>, String> entry : stages.entrySet()) {
         generateStage(doc, schema, parents, entry.getKey(), generatedStages);
      }
   }

   private static void generateStage(Document doc, Element schema, Element[] parents, Class stage, Set<Class<? extends Stage>> generatedStages) {
      if (generatedStages.contains(stage)) return;
      boolean hasParentStage = Stage.class.isAssignableFrom(stage.getSuperclass());
      if (hasParentStage) {
         generateStage(doc, schema, parents, stage.getSuperclass(), generatedStages);
      }
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage)stage.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation == null) return; // not a proper stage

      String stageType = generateClass(doc, schema, stage);
      if (!Modifier.isAbstract(stage.getModifiers()) && !stageAnnotation.internal()) {
         for (Element parent : parents) {
            createReference(doc, parent, XmlHelper.camelCaseToDash(StageHelper.getStageName(stage)), stageType);
         }
         if (!stageAnnotation.deprecatedName().equals(org.radargun.config.Stage.NO_DEPRECATED_NAME)) {
            for (Element parent : parents) {
               createReference(doc, parent, XmlHelper.camelCaseToDash(stageAnnotation.deprecatedName()), stageType);
            }
         }
      }
      generatedStages.add(stage);
   }

   private static String generateClass(Document doc, Element schema, Class<?> clazz) {
      String typeName = class2xmlId(clazz);
      if (generatedTypes.contains(typeName)) {
         return RG_PREFIX + typeName;
      }
      generatedTypes.add(typeName);

      String superType = null;
      if (clazz.getSuperclass() != Object.class) {
         superType = generateClass(doc, schema, clazz.getSuperclass());
      }

      String source = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
      if (source == null) source = "unknown source";
      int lastSlash = source.lastIndexOf('/');
      if (lastSlash > 0) source = source.substring(lastSlash + 1);
      schema.appendChild(doc.createComment("From " +  source));

      Element typeElement = createComplexType(doc, schema, typeName, superType, true,
            Modifier.isAbstract(clazz.getModifiers()), findDocumentation(clazz));
      Element propertiesSequence = createSequence(doc, typeElement);
      for (Map.Entry<String, Path> property : PropertyHelper.getDeclaredProperties(clazz, true, true).entrySet()) {
         generateProperty(doc, schema, typeElement, propertiesSequence, property.getKey(), property.getValue(), true);
      }
      return RG_PREFIX + typeName;
   }

   private static String findDocumentation(Class<?> clazz) {
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage)clazz.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation != null) return stageAnnotation.doc();
      return null;
   }

   private static void generateProperty(Document doc, Element schema, Element parentType, Element parentSequence,
                                        String propertyName, Path path, boolean generateAttributes) {
      if (propertyName.isEmpty()) {
         if (path.isComplete()) {
            throw new IllegalArgumentException("Can't use empty property name this way.");
         } else {
            // we have to put copy all properties directly here
            for (Map.Entry<String, Path> property : PropertyHelper.getDeclaredProperties(path.getTargetType(), true, true).entrySet()) {
               // do not generate attributes as these already have been generated in the parent class
               generateProperty(doc, schema, parentType, parentSequence, property.getKey(), property.getValue(), false);
            }
            return;
         }
      }
      String name = XmlHelper.camelCaseToDash(propertyName);
      String type, documentation = null;
      boolean createElement = true;
      int elementMinOccurs = 0;

      if (!path.isComplete()) {
         type = generateClass(doc, schema, path.getTargetType());
      } else {
         Property propertyAnnotation = path.getTargetAnnotation();
         if (propertyAnnotation == null) {
            throw new IllegalStateException(path.toString());
         }
         if (propertyAnnotation.readonly()) return;
         String propertyDocText = propertyAnnotation.doc();
         if (propertyName.equals(propertyAnnotation.deprecatedName())) {
            propertyDocText = "*DEPRECATED* " + propertyDocText;
         }

         boolean hasComplexConverter = propertyAnnotation.complexConverter() != ComplexConverter.Dummy.class;
         if (hasComplexConverter) {
            type = generateComplexType(doc, schema, path.getTargetType(), propertyAnnotation.complexConverter());
            elementMinOccurs = propertyAnnotation.optional() ? 0 : 1;
         } else {
            type = generateSimpleType(doc, schema, path.getTargetType(), propertyAnnotation.converter());
            if (generateAttributes) {
               addAttribute(doc, parentType, name, type, propertyDocText, !propertyAnnotation.optional());
            }
         }
         // do not write elements for simple mandatory properties - these are required as attributes
         createElement = propertyAnnotation.optional() || hasComplexConverter;
         documentation = propertyAnnotation.doc();
      }
      if (createElement && path.isTrivial()) {
         Element propertyElement = createReference(doc, parentSequence, name, type, elementMinOccurs, 1);
         addDocumentation(doc, propertyElement, documentation);
      }
   }

   private static String class2xmlId(Class<?> clazz) {
      return XmlHelper.camelCaseToDash(clazz.getName().replaceAll("[.$]", "-"));
   }

   private static String generateComplexType(Document doc, Element schema, Class<?> type, Class<? extends ComplexConverter<?>> complexConverterClass) {
      String typeName = class2xmlId(type) + "-converted-by-" + class2xmlId(complexConverterClass);
      if (generatedTypes.contains(typeName)) {
         return RG_PREFIX + typeName;
      }
      generatedTypes.add(typeName);

      Element typeElement = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      typeElement.setAttribute(XS_NAME, typeName);
      ComplexConverter<?> converter;
      try {
         Constructor<? extends ComplexConverter<?>> ctor = complexConverterClass.getDeclaredConstructor();
         ctor.setAccessible(true);
         converter = ctor.newInstance();
      } catch (Exception e) {
         throw new IllegalArgumentException("Cannot create " + complexConverterClass.getName(), e);
      }
      Element choice = createChoice(doc, createSequence(doc, typeElement), converter.minAttributes(), converter.maxAttributes());
      for (Class<?> inner : converter.content()) {
         DefinitionElement de = inner.getAnnotation(DefinitionElement.class);
         if (de == null) throw new IllegalArgumentException(inner.getName());
         String subtypeName = class2xmlId(inner);
         createReference(doc, choice, de.name(), RG_PREFIX + subtypeName);
         if (!generatedTypes.contains(subtypeName)) {
            generatedTypes.add(subtypeName);
            Map<String, Path> subtypeProperties = PropertyHelper.getProperties(inner, true, false, true);
            String extended = null;
            Path valueProperty = subtypeProperties.get("");
            if (valueProperty != null && valueProperty.getTargetAnnotation().complexConverter() != ComplexConverter.Dummy.class) {
               // if we have complex value property, let's inherit from the value converter
               extended = generateComplexType(doc, schema, valueProperty.getTargetType(), valueProperty.getTargetAnnotation().complexConverter());
               subtypeProperties.remove("");
            }
            Element subtypeType = createComplexType(doc, schema, subtypeName, extended, true, false, de.doc());
            Element subtypeSequence = createSequence(doc, subtypeType);
            for (Map.Entry<String, Path> property : subtypeProperties.entrySet()) {
               if (property.getKey().isEmpty()) {
                  throw new IllegalArgumentException("Empty property in class " + inner.getName());
               }
               generateProperty(doc, schema, subtypeType, subtypeSequence, property.getKey(), property.getValue(), true);
            }
         }
      }

      schema.appendChild(typeElement);
      return RG_PREFIX + typeName;
   }

   private static String generateSimpleType(Document doc, Element schema, Class<?> type,
                                            Class<? extends Converter<?>> converterClass) {
      String typeName = class2xmlId(type);
      if (!DefaultConverter.class.equals(converterClass)) {
         typeName += "-converted-by-" + class2xmlId(converterClass);
      }
      if (generatedTypes.contains(typeName)) {
         return RG_PREFIX + typeName;
      }

      Element typeElement = doc.createElementNS(NS_XS, XS_SIMPLE_TYPE);
      typeElement.setAttribute(XS_NAME, typeName);
      // do not hang the element yet - if we can't specify it well, drop that
      Element union = doc.createElementNS(NS_XS, XS_UNION);
      typeElement.appendChild(union);

      Element propertyType = doc.createElementNS(NS_XS, XS_SIMPLE_TYPE);
      union.appendChild(propertyType);
      Element propertyRestriction = doc.createElementNS(NS_XS, XS_RESTRICTION);
      propertyType.appendChild(propertyRestriction);
      if (!DefaultConverter.class.equals(converterClass)) {
         Converter<?> converter;
         try {
            Constructor<? extends Converter<?>> ctor = converterClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            converter = ctor.newInstance();
         } catch (Exception e) {
            System.err.printf("Cannot instantiate converter service %s: %s",
                  converterClass.getName(), e.getMessage());
            return XS_STRING;
         }
         Element propertyPattern = doc.createElementNS(NS_XS, XS_PATTERN);
         propertyRestriction.appendChild(propertyPattern);
         propertyRestriction.setAttribute(XS_BASE, XS_STRING);
         propertyPattern.setAttribute(XS_VALUE, converter.allowedPattern(type));
      } else if (type == Integer.class || type == int.class) {
         propertyRestriction.setAttribute(XS_BASE, XS_INTEGER);
      } else if (type == Long.class || type == long.class) {
         propertyRestriction.setAttribute(XS_BASE, XS_LONG);
      } else if (type == Boolean.class || type == boolean.class) {
         propertyRestriction.setAttribute(XS_BASE, XS_BOOLEAN);
      } else if (type == Float.class || type == float.class) {
         propertyRestriction.setAttribute(XS_BASE, XS_FLOAT);
      } else if (type == Double.class || type == double.class) {
         propertyRestriction.setAttribute(XS_BASE, XS_DOUBLE);
      } else if (type == Number.class) {
         propertyRestriction.setAttribute(XS_BASE, XS_DOUBLE);
      } else if (type.isEnum()) {
         propertyRestriction.setAttribute(XS_BASE, XS_STRING);
         for (Object e : type.getEnumConstants()) {
            Element enumeration = doc.createElementNS(NS_XS, XS_ENUMERATION);
            propertyRestriction.appendChild(enumeration);
            enumeration.setAttribute(XS_VALUE, e.toString());
         }
      } else {
         // all the elements are just dropped
         return XS_STRING;
      }

      Element expressionType = doc.createElementNS(NS_XS, XS_SIMPLE_TYPE);
      union.appendChild(expressionType);
      Element expressionRestriction = doc.createElementNS(NS_XS, XS_RESTRICTION);
      expressionType.appendChild(expressionRestriction);
      expressionRestriction.setAttribute(XS_BASE, XS_STRING);
      Element expressionPattern = doc.createElementNS(NS_XS, XS_PATTERN);
      expressionRestriction.appendChild(expressionPattern);
      expressionPattern.setAttribute(XS_VALUE, "[$#]\\{.*\\}");

      generatedTypes.add(typeName);
      schema.appendChild(typeElement);
      return RG_PREFIX + typeName;
   }

   private static String maxOccurs(int maxOccurs) {
      return maxOccurs < 0 ? "unbounded" : String.valueOf(maxOccurs);
   }

   private static Element createSequence(Document doc, Element parentComplex) {
      Element sequence = doc.createElementNS(NS_XS, XS_SEQUENCE);
      sequence.setAttribute(XS_MIN_OCCURS, "1");
      sequence.setAttribute(XS_MAX_OCCURS, "1");
      parentComplex.appendChild(sequence);
      return sequence;
   }

   private static Element createChoice(Document doc, Element sequence, int minOccurs, int maxOccurs) {
      Element choice = doc.createElementNS(NS_XS, XS_CHOICE);
      choice.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      choice.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      sequence.appendChild(choice);
      return choice;
   }

   private static Element createComplexElement(Document doc, Element parentSequence, String name, int minOccurs, int maxOccurs) {
      Element element = doc.createElementNS(NS_XS, XS_ELEMENT);
      element.setAttribute(XS_NAME, name);
      if (minOccurs >= 0) element.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      element.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      parentSequence.appendChild(element);
      Element complex = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      element.appendChild(complex);
      return complex;
   }

   private static Element createComplexType(Document doc, Element parentSequence, String name, String extended, boolean useComplexContent, boolean isAbstract, String documentation) {
      Element typeElement = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      if (isAbstract) typeElement.setAttribute(XS_ABSTRACT, "true");
      typeElement.setAttribute(XS_NAME, name);
      addDocumentation(doc, typeElement, documentation);
      parentSequence.appendChild(typeElement);
      if (extended == null) {
         return typeElement;
      } else {
         Element content = doc.createElementNS(NS_XS, useComplexContent ? XS_COMPLEX_CONTENT : XS_SIMPLE_CONTENT);
         typeElement.appendChild(content);
         Element extension = doc.createElementNS(NS_XS, XS_EXTENSION);
         extension.setAttribute(XS_BASE, extended);
         content.appendChild(extension);
         return extension;
      }
   }

   private static Element createReference(Document doc, Element parent, String name, String type) {
      return createReference(doc, parent, name, type, -1, -1);
   }

   private static Element createReference(Document doc, Element parent, String name, String type, int minOccurs, int maxOccurs) {
      Element reference = doc.createElementNS(NS_XS, XS_ELEMENT);
      reference.setAttribute(XS_NAME, name);
      reference.setAttribute(XS_TYPE, type);
      if (minOccurs >= 0) reference.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      if (maxOccurs >= 0 || minOccurs >= 0) reference.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      parent.appendChild(reference);
      return reference;
   }

   private static void addAttribute(Document doc, Element complexTypeElement, String name, boolean required) {
      addAttribute(doc, complexTypeElement, name, XS_STRING, null, required);
   }

   private static void addAttribute(Document doc, Element complexTypeElement, String name, String type, String documentation, boolean required) {
      Element attribute = doc.createElementNS(NS_XS, XS_ATTRIBUTE);
      attribute.setAttribute(XS_NAME, name);
      attribute.setAttribute(XS_TYPE, type);
      if (required) {
         attribute.setAttribute(XS_USE, XS_REQUIRED);
      }
      complexTypeElement.appendChild(attribute);
      addDocumentation(doc, attribute, documentation);
   }

   private static void addDocumentation(Document doc, Element element, String documentation) {
      if (documentation != null) {
         Element annotation = doc.createElementNS(NS_XS, XS_ANNOTATION);
         element.appendChild(annotation);
         Element docEl = doc.createElementNS(NS_XS, XS_DOCUMENTATION);
         annotation.appendChild(docEl);
         docEl.appendChild(doc.createTextNode(documentation));
      }
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
      generate(args[0]);
   }
}

