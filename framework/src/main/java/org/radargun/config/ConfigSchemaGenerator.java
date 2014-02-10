package org.radargun.config;

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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.radargun.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Radim Vansa <rvansa@redhat.com>
 * @version 11/21/12
 */
public class ConfigSchemaGenerator {
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
   private static final String XS_EXTENSION = "extension";
   private static final String XS_BASE = "base";
   private static final String XS_VALUE = "value";
   private static final String XS_ABSTRACT = "abstract";
   private static final String RG_ABSTRACT_PRODUCT = RG_PREFIX + "abstractProduct";
   private static final String XS_CHOICE = "choice";
   private static final String XS_ANNOTATION = "annotation";
   private static final String XS_DOCUMENTATION = "documentation";
   private static List<String> products = new ArrayList<String>();
   private static String stageJarFile;
   private static Set<String> simpleTypes = new HashSet<String>();


   public static void generate(String directory) {
      try {
         PrintWriter writer = new PrintWriter(new File(directory + File.separator + "radargun-1.1.xsd"));
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
      schema.setAttribute("targetNamespace", "urn:radargun:benchmark:1.1");
      schema.setAttribute("xmlns:rg", "urn:radargun:benchmark:1.1");
      doc.appendChild(schema);

      Element benchConfig = doc.createElementNS(NS_XS, XS_ELEMENT);
      benchConfig.setAttribute(XS_NAME, "bench-config");
      schema.appendChild(benchConfig);
      Element benchConfigComplex = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      benchConfig.appendChild(benchConfigComplex);
      Element benchConfigSequence = createSequence(doc, benchConfigComplex);

      Element masterComplex = createComplexElement(doc, benchConfigSequence, "master", 1, 1);
      addAttribute(doc, masterComplex, "bindAddress", false);
      addAttribute(doc, masterComplex, "port", false);

      Element benchmarkComplex = createComplexElement(doc, benchConfigSequence, "benchmark", 1, 1);
      Element benchmarkChoice = createChoice(doc, createSequence(doc, benchmarkComplex), 1, -1);
      Element repeatType = createComplexType(doc, schema, "repeat", null, false, null);
      Element repeatChoice = createChoice(doc, createSequence(doc, repeatType), 1, -1);
      addAttribute(doc, repeatType, "times", false);
      addAttribute(doc, repeatType, "from", false);
      addAttribute(doc, repeatType, "to", false);
      addAttribute(doc, repeatType, "inc", false);
      addAttribute(doc, repeatType, "name", false);
      createReference(doc, benchmarkChoice, "Repeat", RG_PREFIX + "repeat");
      createReference(doc, repeatChoice, "Repeat", RG_PREFIX + "repeat");
      generateStageDefinitions(doc, schema, new Element[]{benchmarkChoice, repeatChoice});

      addAttribute(doc, benchmarkComplex, "initSize", false);
      addAttribute(doc, benchmarkComplex, "maxSize", false);
      addAttribute(doc, benchmarkComplex, "increment", false);

      Element productsComplex = createComplexElement(doc, benchConfigSequence, "products", 1, 1);
      Element productsSequence = createSequence(doc, productsComplex);

      Element abstractProductType = createComplexType(doc, schema, "abstractProduct", null, true, null);
      Element abstractProductSequence = createSequence(doc, abstractProductType);
      Element configComplex = createComplexElement(doc, abstractProductSequence, "config", 1, -1);
      Element configSequence = createSequence(doc, configComplex);
      addAttribute(doc, configComplex, "name", false);
      addAttribute(doc, configComplex, "file", false);
      addAttribute(doc, configComplex, "cache", false);
      addAttribute(doc, configComplex, "wrapper", false);
      Element wrapperType = createComplexType(doc, schema, "wrapper", null, false, null);
      Element wrapperSequence = createSequence(doc, wrapperType);
      Element wrapperProperty = createComplexElement(doc, wrapperSequence, "property", 0, -1);
      addAttribute(doc, wrapperProperty, "name", true);
      addAttribute(doc, wrapperProperty, "value", true);
      addAttribute(doc, wrapperType, "class", false);
      Element wrapper = createReference(doc, configSequence, "wrapper", RG_PREFIX + "wrapper");
      wrapper.setAttribute(XS_MIN_OCCURS, "0");
      wrapper.setAttribute(XS_MAX_OCCURS, "1");
      Element siteComplex = createComplexElement(doc, configSequence, "site", 0, -1);
      Element siteSequence = createSequence(doc, siteComplex);
      wrapper = createReference(doc, siteSequence, "wrapper", RG_PREFIX + "wrapper");
      wrapper.setAttribute(XS_MIN_OCCURS, "0");
      wrapper.setAttribute(XS_MAX_OCCURS, "1");
      addAttribute(doc, siteComplex, "name", false);
      addAttribute(doc, siteComplex, "config", false);
      addAttribute(doc, siteComplex, "slaves", false);
      addAttribute(doc, siteComplex, "cache", false);

      Element productChoice = createChoice(doc, productsSequence, 1, -1);
      Element genericProductType = createComplexType(doc, schema, "product", RG_ABSTRACT_PRODUCT, false, null);
      addAttribute(doc, genericProductType, "name", false);
      createReference(doc, productChoice, "product", RG_PREFIX + "product");
      for (String productName : products) {
         createComplexType(doc, schema, productName, RG_ABSTRACT_PRODUCT, false, null);
         createReference(doc, productChoice, productName, RG_PREFIX + productName);
      }

      Element reportsComplex = createComplexElement(doc, benchConfigSequence, "reports", 1, 1);
      Element reportsSequence = createSequence(doc, reportsComplex);
      Element reportComplex = createComplexElement(doc, reportsSequence, "report", 0, -1);
      Element reportSequence = createSequence(doc, reportComplex);
      addAttribute(doc, reportComplex, "name", false);
      addAttribute(doc, reportComplex, "reportDirectory", false);
      addAttribute(doc, reportComplex, "csvFilesDirectory", false);
      addAttribute(doc, reportComplex, "includeAll", false);
      Element itemComplex = createComplexElement(doc, reportSequence, "item", 0, -1);
      addAttribute(doc, itemComplex, "product", false);
      addAttribute(doc, itemComplex, "config", false);
   }

   private static void generateStageDefinitions(Document doc, Element schema, Element[] parents) {
      Set<Class> generatedStages = new HashSet<Class>();
      for (Class<?> stage : StageHelper.getStagesFromJar(stageJarFile).values()) {
         generateStage(doc, schema, parents, stage, generatedStages);
      }
   }

   private static void generateStage(Document doc, Element schema, Element[] parents, Class stage, Set<Class> generatedStages) {
      if (generatedStages.contains(stage)) return;
      boolean hasParentStage = Stage.class.isAssignableFrom(stage.getSuperclass());
      if (hasParentStage) {
         generateStage(doc, schema, parents, stage.getSuperclass(), generatedStages);
      }
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage)stage.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation == null) return; // not a proper stage

      String stageName = StageHelper.getStageName(stage);
      String stageDocText = stageAnnotation.doc();
      Element stageType = createComplexType(doc, schema, stageName,
            hasParentStage ? RG_PREFIX + StageHelper.getStageName(stage.getSuperclass()) : null,
            Modifier.isAbstract(stage.getModifiers()),
            stageDocText);
      for (Element parent : parents) {
         createReference(doc, parent, stageName, RG_PREFIX + stageName);
      }
      if (!stageAnnotation.deprecatedName().equals(org.radargun.config.Stage.NO_DEPRECATED_NAME)) {
         for (Element parent : parents) {
            createReference(doc, parent, stageAnnotation.deprecatedName(), RG_PREFIX + stageName);
         }
      }
      for (Map.Entry<String, Field> property : PropertyHelper.getDeclaredProperties(stage).entrySet()) {
         Property propertyAnnotation = property.getValue().getAnnotation(Property.class);
         if (propertyAnnotation.readonly()) continue; // cannot be configured
         String propertyDocText = propertyAnnotation.doc();
         if (property.getKey().equals(propertyAnnotation.deprecatedName())) {
            propertyDocText = "*DEPRECATED* " + propertyDocText;
         }
         String type = generateType(doc, schema, property.getValue().getType(), propertyAnnotation.converter());
         addAttribute(doc, stageType, property.getKey(), type, propertyDocText, !propertyAnnotation.optional());
      }
      generatedStages.add(stage);
   }

   private static String generateType(Document doc, Element schema, Class<?> type, Class<? extends Converter<?>> converterClass) {
      Converter<?> converter;
      try {
         converter = converterClass.newInstance();
      } catch (Exception e) {
         System.err.printf("Cannot instantiate converter type %s: %s",
               converterClass.getName(), e.getMessage());
         return XS_STRING;
      }
      String typeName = type.getName().replaceAll("[.$]", "_");
      if (!(converter instanceof DefaultConverter)) {
         typeName += "__" + converter.getClass().getName().replaceAll("[.$]", "_");
      }
      if (simpleTypes.contains(typeName)) {
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
      if (!(converter instanceof DefaultConverter)) {
         Element propertyPattern = doc.createElementNS(NS_XS, XS_PATTERN);
         propertyRestriction.appendChild(propertyPattern);
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

      simpleTypes.add(typeName);
      schema.appendChild(typeElement);
      return RG_PREFIX + typeName;
   }

   private static List<String> getStageNames() {
      List<String> stageNames = new ArrayList<String>();
      try {
         ZipInputStream inputStream = new ZipInputStream(new FileInputStream(stageJarFile));
         Pattern pattern = Pattern.compile("org/radargun/stages/(.*)Stage.class");
         for(;;) {
            ZipEntry entry = inputStream.getNextEntry();
            if (entry == null) break;
            Matcher m = pattern.matcher(entry.getName());
            if (m.matches()) {
               stageNames.add(m.group(1));
            }
         }
      } catch (IOException e) {
         System.err.println("Failed to open " + stageJarFile);
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      return stageNames;
   }

   private static Element createSequence(Document doc, Element parentComplex) {
      Element sequence = doc.createElementNS(NS_XS, XS_SEQUENCE);
      parentComplex.appendChild(sequence);
      return sequence;
   }

   private static Element createChoice(Document doc, Element productsSequence, int minOccurs, int maxOccurs) {
      Element choice = doc.createElementNS(NS_XS, XS_CHOICE);
      choice.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      choice.setAttribute(XS_MAX_OCCURS, maxOccurs < 0 ? "unbounded" : String.valueOf(maxOccurs));
      productsSequence.appendChild(choice);
      return choice;
   }

   private static Element createComplexElement(Document doc, Element parentSequence, String name, int minOccurs, int maxOccurs) {
      Element element = doc.createElementNS(NS_XS, XS_ELEMENT);
      element.setAttribute(XS_NAME, name);
      if (minOccurs >= 0) element.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      element.setAttribute(XS_MAX_OCCURS, maxOccurs < 0 ? "unbounded" : String.valueOf(maxOccurs));
      parentSequence.appendChild(element);
      Element complex = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      element.appendChild(complex);
      return complex;
   }

   private static Element createComplexType(Document doc, Element parentSequence, String name, String extended, boolean isAbstract, String documentation) {
      Element typeElement = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      if (isAbstract) typeElement.setAttribute(XS_ABSTRACT, "true");
      typeElement.setAttribute(XS_NAME, name);
      addDocumentation(doc, typeElement, documentation);
      parentSequence.appendChild(typeElement);
      if (extended == null) {
         return typeElement;
      } else {
         Element complexContent = doc.createElementNS(NS_XS, XS_COMPLEX_CONTENT);
         typeElement.appendChild(complexContent);
         Element extension = doc.createElementNS(NS_XS, XS_EXTENSION);
         extension.setAttribute(XS_BASE, extended);
         complexContent.appendChild(extension);
         return extension;
      }
   }

   private static Element createReference(Document doc, Element parent, String name, String type) {
      Element reference = doc.createElementNS(NS_XS, XS_ELEMENT);
      reference.setAttribute(XS_NAME, name);
      reference.setAttribute(XS_TYPE, type);
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

   public static void main(String[] args) {
      if (args.length < 1) System.err.println("No schema location directory specified!");
      if (args.length < 2) System.err.println("No jar file with stages specified!");
      stageJarFile = args[1];
      for (int i = 2; i < args.length; ++i) products.add(args[i]);
      generate(args[0]);
   }
}

