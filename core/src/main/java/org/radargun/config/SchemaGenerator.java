package org.radargun.config;

import org.radargun.utils.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for generators of schemas.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class SchemaGenerator {

   protected static final String NS_XS = "http://www.w3.org/2001/XMLSchema";
   protected static final String XS_ELEMENT = "element";
   protected static final String XS_COMPLEX_TYPE = "complexType";
   protected static final String XS_NAME = "name";
   protected static final String THIS_PREFIX = "this:";

   protected static final String XS_ANY = "any";
   protected static final String XS_RESTRICTION = "restriction";
   protected static final String XS_PATTERN = "pattern";
   protected static final String XS_ENUMERATION = "enumeration";
   protected static final String XS_SIMPLE_TYPE = "simpleType";
   protected static final String XS_SEQUENCE = "sequence";
   protected static final String XS_UNION = "union";
   protected static final String XS_MIN_OCCURS = "minOccurs";
   protected static final String XS_MAX_OCCURS = "maxOccurs";
   protected static final String XS_ATTRIBUTE = "attribute";
   protected static final String XS_TYPE = "type";
   protected static final String XS_USE = "use";
   protected static final String XS_REQUIRED = "required";
   protected static final String XS_STRING = "string";
   protected static final String XS_INTEGER = "integer";
   protected static final String XS_LONG = "long";
   protected static final String XS_FLOAT = "float";
   protected static final String XS_DOUBLE = "double";
   protected static final String XS_BOOLEAN = "boolean";
   protected static final String XS_COMPLEX_CONTENT = "complexContent";
   protected static final String XS_SIMPLE_CONTENT = "simpleContent";
   protected static final String XS_EXTENSION = "extension";
   protected static final String XS_BASE = "base";
   protected static final String XS_VALUE = "value";
   protected static final String XS_ABSTRACT = "abstract";
   protected static final String XS_CHOICE = "choice";
   protected static final String XS_ANNOTATION = "annotation";
   protected static final String XS_DOCUMENTATION = "documentation";
   protected static final String XS_NAMESPACE = "namespace";
   protected static final String XS_OTHER_NAMESPACE = "##other";
   protected static final String XS_ANY_NAMESPACE = "##any";
   protected static final String XS_INCLUDE = "include";
   protected static final String XS_IMPORT = "import";
   protected static final String XS_SCHEMA_LOCATION = "schemaLocation";

   protected final String namespaceRoot;
   protected final String namespace;
   protected final String omitPrefix;
   private final Map<String, String> importedNamespaces = new HashMap<>();
   private int nsCounter = 0;

   protected Document doc;
   protected Element schema;

   protected String intType;
   protected Map<String, String> generatedTypes = new HashMap<>();

   public SchemaGenerator(String namespaceRoot, String namespace, String omitPrefix) {
      this.namespaceRoot = namespaceRoot;
      this.namespace = namespace;
      this.omitPrefix = omitPrefix;
   }

   /**
    * For reusing the generator within another document
    * @param doc
    * @param schema
    */
   void setDocSchema(Document doc, Element schema) {
      this.doc = doc;
      this.schema = schema;
   }

   protected String generateClass(Class<?> clazz) {
      String typeName = class2xmlId(clazz);
      String fullName = generatedTypes.get(typeName);
      if (fullName != null) {
         return fullName;
      }
      String superType = null;
      if (clazz.getSuperclass() != Object.class) {
         superType = generateClass(clazz.getSuperclass());
      }
      NamespaceHelper.Coords coords = NamespaceHelper.getCoords(namespaceRoot, clazz,  omitPrefix);
      if (coords == null || coords.namespace.equals(this.namespace)) {
         String source = Utils.getCodePath(clazz);
         if (source == null) source = "unknown source";
         int lastSlash = source.lastIndexOf('/');
         if (lastSlash > 0) source = source.substring(lastSlash + 1);
         schema.appendChild(doc.createComment("From " + source));

         Element typeElement = createComplexType(typeName, superType, true,
               Modifier.isAbstract(clazz.getModifiers()), findDocumentation(clazz));
         Element propertiesSequence = createSequence(typeElement);
         for (Map.Entry<String, Path> property : PropertyHelper.getDeclaredProperties(clazz, true, true)) {
            generateProperty(typeElement, propertiesSequence, property.getKey(), property.getValue(), true);
         }
         fullName = BenchmarkSchemaGenerator.THIS_PREFIX + typeName;
      } else {
         fullName = requireImport(coords.namespace) + typeName;
      }
      generatedTypes.put(typeName, fullName);
      return fullName;
   }

   protected Element createAny(Element parent) {
      return createAny(parent, 0, 1, XS_OTHER_NAMESPACE);
   }

   protected Element createAny(Element parent, int minOccurs, int maxOccurs, String namespace) {
      Element any = doc.createElementNS(NS_XS, XS_ANY);
      any.setAttribute(XS_NAMESPACE, namespace);
      any.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      any.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      parent.appendChild(any);
      return any;
   }

   protected abstract String findDocumentation(Class<?> clazz);

   private void generateProperty(Element parentType, Element parentSequence,
                                 String propertyName, Path path, boolean generateAttributes) {
      if (propertyName.isEmpty()) {
         if (path.isComplete()) {
            throw new IllegalArgumentException("Can't use empty property name this way.");
         } else {
            // we have to put copy all properties directly here
            for (Map.Entry<String, Path> property : PropertyHelper.getDeclaredProperties(path.getTargetType(), true, true)) {
               // do not generate attributes as these already have been generated in the parent class
               generateProperty(parentType, parentSequence, property.getKey(), property.getValue(), false);
            }
            return;
         }
      }
      String name = XmlHelper.camelCaseToDash(propertyName);
      String type, documentation = null;
      boolean createElement = true;
      int elementMinOccurs = 0;

      if (!path.isComplete()) {
         type = generateClass(path.getTargetType());
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
            type = generateComplexType(path.getTargetType(), propertyAnnotation.complexConverter());
            elementMinOccurs = propertyAnnotation.optional() ? 0 : 1;
         } else {
            type = generateSimpleType(path.getTargetType(), propertyAnnotation.converter());
            if (generateAttributes) {
               // property with non-trivial path can be declared in delegated element
               addAttribute(parentType, name, type, propertyDocText, !propertyAnnotation.optional() && path.isTrivial());
            }
         }
         // do not write elements for simple mandatory properties - these are required as attributes
         createElement = propertyAnnotation.optional() || hasComplexConverter;
         documentation = propertyAnnotation.doc();
      }
      if (createElement && path.isTrivial()) {
         Element propertyElement = createReference(parentSequence, name, type, elementMinOccurs, 1);
         addDocumentation(propertyElement, documentation);
      }
   }

   protected String class2xmlId(Class<?> clazz) {
      return XmlHelper.camelCaseToDash(clazz.getName().replaceAll("[.$]", "-"));
   }

   private String generateComplexType(Class<?> type, Class<? extends ComplexConverter<?>> complexConverterClass) {
      String typeName = class2xmlId(type) + "-converted-by-" + class2xmlId(complexConverterClass);
      String fullName = generatedTypes.get(typeName);
      if (fullName != null) {
         return fullName;
      }
      generatedTypes.put(typeName, THIS_PREFIX + typeName);

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
      if (converter instanceof DefinitionElementConverter) {
         DefinitionElementConverter<?> dec = (DefinitionElementConverter<?>) converter;
         Element choice = createChoice(createSequence(typeElement), dec.minAttributes(), dec.maxAttributes());
         for (Class<?> inner : dec.content()) {
            DefinitionElement de = inner.getAnnotation(DefinitionElement.class);
            if (de == null) throw new IllegalArgumentException(inner.getName());
            String subtypeName = class2xmlId(inner);
            NamespaceHelper.Coords deCoords = NamespaceHelper.getCoords(namespaceRoot, inner, omitPrefix);
            if (deCoords == null || deCoords.namespace.equals(this.namespace)) {
               if (!generatedTypes.containsKey(subtypeName)) {
                  generatedTypes.put(subtypeName, THIS_PREFIX + subtypeName);
                  Map<String, Path> subtypeProperties = PropertyHelper.getProperties(inner, true, false, true);
                  String extended = null;
                  Path valueProperty = subtypeProperties.get("");
                  if (valueProperty != null && valueProperty.getTargetAnnotation().complexConverter() != ComplexConverter.Dummy.class) {
                     // if we have complex value property, let's inherit from the value converter
                     extended = generateComplexType(valueProperty.getTargetType(), valueProperty.getTargetAnnotation().complexConverter());
                     subtypeProperties.remove("");
                  }
                  Element subtypeType = createComplexType(subtypeName, extended, true, false, de.doc());
                  Element subtypeSequence = createSequence(subtypeType);
                  for (Map.Entry<String, Path> property : subtypeProperties.entrySet()) {
                     if (property.getKey().isEmpty()) {
                        throw new IllegalArgumentException("Empty property in class " + inner.getName());
                     }
                     generateProperty(subtypeType, subtypeSequence, property.getKey(), property.getValue(), true);
                  }
               }
               createReference(choice, de.name(), THIS_PREFIX + subtypeName);
            } else {
               createComplexElement(choice, de.name(), 1, 1, requireImport(deCoords.namespace) + subtypeName, de.doc());
            }
         }
      } else {
         createAny(createSequence(typeElement), 1, -1, XS_OTHER_NAMESPACE);
      }

      schema.appendChild(typeElement);
      return BenchmarkSchemaGenerator.THIS_PREFIX + typeName;
   }

   protected String generateSimpleType(Class<?> type,
                                       Class<? extends Converter<?>> converterClass) {
      String typeName = class2xmlId(type);
      if (!DefaultConverter.class.equals(converterClass)) {
         typeName += "-converted-by-" + class2xmlId(converterClass);
      }
      String fullName = generatedTypes.get(typeName);
      if (fullName != null) {
         return fullName;
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
            try {
               DocumentedValue documentedValue = type.getField(e.toString()).getAnnotation(DocumentedValue.class);
               if (documentedValue != null) {
                  Element annotation = doc.createElementNS(NS_XS, XS_ANNOTATION);
                  enumeration.appendChild(annotation);
                  Element documentation = doc.createElementNS(NS_XS, XS_DOCUMENTATION);
                  annotation.appendChild(documentation);
                  documentation.setTextContent(documentedValue.value());
               }
            } catch (NoSuchFieldException e1) {
               throw new IllegalStateException("Enum should always have its constants as fields!", e1);
            }

         }
      } else {
         // all the elements are just dropped
         generatedTypes.put(typeName, XS_STRING);
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

      generatedTypes.put(typeName, BenchmarkSchemaGenerator.THIS_PREFIX + typeName);
      schema.appendChild(typeElement);
      return BenchmarkSchemaGenerator.THIS_PREFIX + typeName;
   }

   private String maxOccurs(int maxOccurs) {
      return maxOccurs < 0 ? "unbounded" : String.valueOf(maxOccurs);
   }

   protected Element createSequence(Element parentComplex) {
      Element sequence = doc.createElementNS(NS_XS, XS_SEQUENCE);
      sequence.setAttribute(XS_MIN_OCCURS, "1");
      sequence.setAttribute(XS_MAX_OCCURS, "1");
      parentComplex.appendChild(sequence);
      return sequence;
   }

   protected Element createChoice(Element sequence, int minOccurs, int maxOccurs) {
      Element choice = doc.createElementNS(NS_XS, XS_CHOICE);
      choice.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      choice.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      sequence.appendChild(choice);
      return choice;
   }

   protected Element createComplexElement(Element parentSequence, String name, int minOccurs, int maxOccurs, String doc) {
      Element element = this.doc.createElementNS(NS_XS, XS_ELEMENT);
      element.setAttribute(XS_NAME, name);
      if (minOccurs >= 0) element.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      element.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      addDocumentation(element, doc);
      parentSequence.appendChild(element);
      Element complex = this.doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      element.appendChild(complex);
      return complex;
   }

   protected Element createComplexElement(Element parentSequence, String name, int minOccurs, int maxOccurs, String extended, String doc) {
      Element complexType = createComplexElement(parentSequence, name, minOccurs, maxOccurs, doc);
      Element content = this.doc.createElementNS(NS_XS, XS_COMPLEX_CONTENT);
      complexType.appendChild(content);
      Element extension = this.doc.createElementNS(NS_XS, XS_EXTENSION);
      extension.setAttribute(XS_BASE, extended);
      content.appendChild(extension);
      return complexType;
   }

   protected Element createComplexType(String name, String extended, boolean useComplexContent, boolean isAbstract, String documentation) {
      Element typeElement = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      if (isAbstract) typeElement.setAttribute(XS_ABSTRACT, "true");
      typeElement.setAttribute(XS_NAME, name);
      addDocumentation(typeElement, documentation);
      schema.appendChild(typeElement);
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

   protected Element createReference(Node parent, String name, String type) {
      return createReference(parent, name, type, -1, -1);
   }

   protected Element createReference(Node parent, String name, String type, int minOccurs, int maxOccurs) {
      Element reference = doc.createElementNS(NS_XS, XS_ELEMENT);
      reference.setAttribute(XS_NAME, name);
      reference.setAttribute(XS_TYPE, type);
      if (minOccurs >= 0) reference.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      if (maxOccurs >= 0 || minOccurs >= 0) reference.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      parent.appendChild(reference);
      return reference;
   }

   protected void addAttribute(Element complexTypeElement, String name, boolean required) {
      addAttribute(complexTypeElement, name, XS_STRING, null, required);
   }

   protected void addAttribute(Element complexTypeElement, String name, String type, String documentation, boolean required) {
      Element attribute = doc.createElementNS(NS_XS, XS_ATTRIBUTE);
      attribute.setAttribute(XS_NAME, name);
      attribute.setAttribute(XS_TYPE, type);
      if (required) {
         attribute.setAttribute(XS_USE, XS_REQUIRED);
      }
      complexTypeElement.appendChild(attribute);
      addDocumentation(attribute, documentation);
   }

   private void addDocumentation(Element element, String documentation) {
      if (documentation != null) {
         Element annotation = doc.createElementNS(NS_XS, XS_ANNOTATION);
         element.appendChild(annotation);
         Element docEl = doc.createElementNS(NS_XS, XS_DOCUMENTATION);
         annotation.appendChild(docEl);
         docEl.appendChild(doc.createTextNode(documentation));
      }
   }

   protected void generate(String directory, String filename) {
      try {
         PrintWriter writer = new PrintWriter(new File(directory + File.separator + filename));
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         DocumentBuilder builder = factory.newDocumentBuilder();
         doc = builder.newDocument();
         doc.setXmlVersion("1.0");
         doc.setXmlStandalone(true);
         generate();

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

   protected Element createSchemaElement(String namespace) {
      schema = doc.createElementNS(NS_XS, "schema");
      schema.setAttribute("attributeFormDefault", "unqualified");
      schema.setAttribute("elementFormDefault", "qualified");
      schema.setAttribute("version", "1.0");
      schema.setAttribute("targetNamespace", namespace);
      schema.setAttribute("xmlns:this", namespace);
      doc.appendChild(schema);
      return schema;
   }
   /**
    * Generates scheme file
    */
   protected abstract void generate();

   /**
    * Adds include tag to the parent
    *
    * @param parent  to which include is added
    * @param location of include file e.g. scenario.xsd
    * @return modified parent
    */
   protected Element addInclude(Element parent, String location) {
      Element include = doc.createElementNS(NS_XS, XS_INCLUDE);
      include.setAttribute(XS_SCHEMA_LOCATION, location);
      parent.appendChild(include);
      return include;
   }

   protected Element addImport(String namespace, String location, String shortNs) {
      Element imported = doc.createElementNS(NS_XS, XS_IMPORT);
      imported.setAttribute(XS_NAMESPACE, namespace);
      imported.setAttribute(XS_SCHEMA_LOCATION, location);

      Node firstChild = schema.getFirstChild();
      if (firstChild == null) {
         schema.appendChild(imported);
      } else {
         schema.insertBefore(imported, firstChild);
      }
      schema.setAttribute("xmlns:" + shortNs, namespace);
      return imported;
   }

   protected String requireImport(String namespace) {
      String shortNs = importedNamespaces.get(namespace);
      if (shortNs != null) return shortNs;
      String jarMajorMinor = NamespaceHelper.getJarMajorMinor(namespace);
      if (jarMajorMinor == null) {
         throw new IllegalStateException("Unknown jar for " + namespace);
      }
      shortNs = jarMajorMinor.replaceAll("[^a-zA-Z]", "");
      if (shortNs.startsWith("radargun")) {
         shortNs = shortNs.substring(8);
      }
      shortNs = String.format("%s%02d", shortNs, nsCounter++);
      addImport(namespace, jarMajorMinor + ".xsd", shortNs);
      String shortNsWithColon = shortNs + ':';
      importedNamespaces.put(namespace, shortNsWithColon);
      return shortNsWithColon;
   }
}