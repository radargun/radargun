package org.radargun.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
   protected static final String RG_PREFIX = "rg:";

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

   protected Document doc;
   protected Element schema;

   protected String intType;
   protected Set<String> generatedTypes = new HashSet<>();

   protected String generateClass(Class<?> clazz) {
      String typeName = class2xmlId(clazz);
      if (generatedTypes.contains(typeName)) {
         return ConfigSchemaGenerator.RG_PREFIX + typeName;
      }
      generatedTypes.add(typeName);

      String superType = null;
      if (clazz.getSuperclass() != Object.class) {
         superType = generateClass(clazz.getSuperclass());
      }

      String source = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
      if (source == null) source = "unknown source";
      int lastSlash = source.lastIndexOf('/');
      if (lastSlash > 0) source = source.substring(lastSlash + 1);
      schema.appendChild(doc.createComment("From " + source));

      Element typeElement = createComplexType(schema, typeName, superType, true,
            Modifier.isAbstract(clazz.getModifiers()), findDocumentation(clazz));
      Element propertiesSequence = createSequence(typeElement);
      for (Map.Entry<String, Path> property : PropertyHelper.getDeclaredProperties(clazz, true, true).entrySet()) {
         generateProperty(typeElement, propertiesSequence, property.getKey(), property.getValue(), true);
      }
      return ConfigSchemaGenerator.RG_PREFIX + typeName;
   }

   protected Element createAny(Element parent) {
      return createAny(parent, 0, 1);
   }

   protected Element createAny(Element parent, int minOccurs, int maxOccurs) {
      Element any = doc.createElementNS(NS_XS, XS_ANY);
      any.setAttribute(XS_NAMESPACE, XS_OTHER_NAMESPACE);
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
            for (Map.Entry<String, Path> property : PropertyHelper.getDeclaredProperties(path.getTargetType(), true, true).entrySet()) {
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
      if (generatedTypes.contains(typeName)) {
         return ConfigSchemaGenerator.RG_PREFIX + typeName;
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
      if (converter instanceof DefinitionElementConverter) {
         DefinitionElementConverter<?> dec = (DefinitionElementConverter<?>) converter;
         Element choice = createChoice(createSequence(typeElement), dec.minAttributes(), dec.maxAttributes());
         for (Class<?> inner : dec.content()) {
            DefinitionElement de = inner.getAnnotation(DefinitionElement.class);
            if (de == null) throw new IllegalArgumentException(inner.getName());
            String subtypeName = class2xmlId(inner);
            createReference(choice, de.name(), ConfigSchemaGenerator.RG_PREFIX + subtypeName);
            if (!generatedTypes.contains(subtypeName)) {
               generatedTypes.add(subtypeName);
               Map<String, Path> subtypeProperties = PropertyHelper.getProperties(inner, true, false, true);
               String extended = null;
               Path valueProperty = subtypeProperties.get("");
               if (valueProperty != null && valueProperty.getTargetAnnotation().complexConverter() != ComplexConverter.Dummy.class) {
                  // if we have complex value property, let's inherit from the value converter
                  extended = generateComplexType(valueProperty.getTargetType(), valueProperty.getTargetAnnotation().complexConverter());
                  subtypeProperties.remove("");
               }
               Element subtypeType = createComplexType(schema, subtypeName, extended, true, false, de.doc());
               Element subtypeSequence = createSequence(subtypeType);
               for (Map.Entry<String, Path> property : subtypeProperties.entrySet()) {
                  if (property.getKey().isEmpty()) {
                     throw new IllegalArgumentException("Empty property in class " + inner.getName());
                  }
                  generateProperty(subtypeType, subtypeSequence, property.getKey(), property.getValue(), true);
               }
            }
         }
      } else {
         createAny(createSequence(typeElement), 1, -1);
      }

      schema.appendChild(typeElement);
      return ConfigSchemaGenerator.RG_PREFIX + typeName;
   }

   protected String generateSimpleType(Class<?> type,
                                       Class<? extends Converter<?>> converterClass) {
      String typeName = class2xmlId(type);
      if (!DefaultConverter.class.equals(converterClass)) {
         typeName += "-converted-by-" + class2xmlId(converterClass);
      }
      if (generatedTypes.contains(typeName)) {
         return ConfigSchemaGenerator.RG_PREFIX + typeName;
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
      return ConfigSchemaGenerator.RG_PREFIX + typeName;
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

   protected Element createComplexElement(Element parentSequence, String name, int minOccurs, int maxOccurs) {
      Element element = doc.createElementNS(NS_XS, XS_ELEMENT);
      element.setAttribute(XS_NAME, name);
      if (minOccurs >= 0) element.setAttribute(XS_MIN_OCCURS, String.valueOf(minOccurs));
      element.setAttribute(XS_MAX_OCCURS, maxOccurs(maxOccurs));
      parentSequence.appendChild(element);
      Element complex = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      element.appendChild(complex);
      return complex;
   }

   protected Element createComplexType(Element parentSequence, String name, String extended, boolean useComplexContent, boolean isAbstract, String documentation) {
      Element typeElement = doc.createElementNS(NS_XS, XS_COMPLEX_TYPE);
      if (isAbstract) typeElement.setAttribute(XS_ABSTRACT, "true");
      typeElement.setAttribute(XS_NAME, name);
      addDocumentation(typeElement, documentation);
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

   protected Element createReference(Element parent, String name, String type) {
      return createReference(parent, name, type, -1, -1);
   }

   protected Element createReference(Element parent, String name, String type, int minOccurs, int maxOccurs) {
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
      schema.setAttribute("targetNamespace", "urn:" + namespace);
      schema.setAttribute("xmlns:rg", "urn:" + namespace);
      doc.appendChild(schema);
      return schema;
   }

   protected abstract void generate();
}
