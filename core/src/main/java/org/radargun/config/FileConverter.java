package org.radargun.config;

import java.lang.reflect.Type;

/**
 * Allow the file be converter with different approaches
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class FileConverter implements Converter<String> {

   // we don't need multiple instances
   private static final XsltConverter XSLT_CONVERTER = new XsltConverter();

   @Override
   public String convert(String value, Type type) {
      return convertToString(value);
   }

   @Override
   public String convertToString(String value) {
      if (value != null && !value.isEmpty()) {
         if (XSLT_CONVERTER.isXsltAttribute(value)) {
            value = XSLT_CONVERTER.convertToString(value);
         }
      }
      return value;
   }

   @Override
   public String allowedPattern(Type type) {
      return ".*";
   }
}
