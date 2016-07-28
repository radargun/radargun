package org.radargun.config;

import java.lang.reflect.Type;

/**
 * Converts definition back to XML. This is particularly useful when we want to inline
 * configuration from external schema into benchmark config, as e.g.:
 *
 * <setup plugin="my-plugin">
 *    <my-service xmlns="urn:radargun:plugins:my-plugin:2.1" ...>
 *       <external-configuration>
 *           <foo-bar xmlns="urn:foo:bar:1.0" />
 *       </external-configuration>
 *    </my-service>
 * </setup>
 *
 * where external-configuration is a String property with {@link Property#complexConverter()} set to {@link XmlConverter}.
 */
public class XmlConverter implements ComplexConverter<String> {
   @Override
   public String convert(ComplexDefinition definition, Type type) {
      StringBuilder sb = new StringBuilder();
      appendDefinition(definition, sb, 0);
      return sb.toString();
   }

   private void appendDefinition(ComplexDefinition definition, StringBuilder sb, int indent) {
      for (ComplexDefinition.Entry entry : definition.getAttributes()) {
         if (entry.definition instanceof SimpleDefinition) {
            SimpleDefinition simpleDefinition = (SimpleDefinition) entry.definition;
            if (simpleDefinition.source == SimpleDefinition.Source.TEXT) {
               appendIndent(sb, indent);
               if (!entry.name.isEmpty()) sb.append('<').append(entry.name).append('>');
               sb.append(simpleDefinition.value);
               if (!entry.name.isEmpty()) sb.append("</").append(entry.name).append(">\n");
            }
         } else if (entry.definition instanceof ComplexDefinition) {
            ComplexDefinition complexDefinition = (ComplexDefinition) entry.definition;
            appendIndent(sb, indent).append('<').append(entry.name);
            if (complexDefinition.getNamespace() != null) {
               sb.append(" xlmns=\"").append(complexDefinition.getNamespace()).append('"');
            }
            for (ComplexDefinition.Entry e2 : complexDefinition.getAttributes()) {
               if (e2.definition instanceof SimpleDefinition) {
                  SimpleDefinition sd2 = (SimpleDefinition) e2.definition;
                  if (sd2.source == SimpleDefinition.Source.ATTRIBUTE) {
                     sb.append(' ').append(e2.name).append("=\"").append(sd2.value).append('"');
                  }
               }
            }
            sb.append(">\n");
            appendDefinition(complexDefinition, sb, indent + 4);
            appendIndent(sb, indent).append("</").append(entry.name).append(">\n");
         }
      }
   }

   private StringBuilder appendIndent(StringBuilder sb, int indent) {
      for (; indent > 0; --indent) sb.append(' ');
      return sb;
   }

   @Override
   public String convertToString(String value) {
      return value;
   }
}
