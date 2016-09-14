package org.radargun.utils;

import java.lang.reflect.Type;
import org.radargun.config.Converter;

/**
 * Converter that parses string addresses in form "host:port" or "host" separated by semicolon to String array
 *
 * @author Roman Macor (rmacor@redhat.com)
 */
public class AddressStringListConverter implements Converter<String[]> {
   private static final String PATTERN_STRING = "(\\[([0-9A-Fa-f:]+)\\]|([^:/?#]*))(?::(\\d*))?";

   @Override
   public String[] convert(String string, Type type) {
      return string.split(";");
   }

   @Override
   public String convertToString(String[] values) {
      StringBuilder sb = new StringBuilder();
      String delimiter = "";
      for (String value : values) {
         sb.append(delimiter);
         sb.append(value);
         delimiter = ";";
      }
      return sb.toString();
   }

   @Override
   public String allowedPattern(Type type) {
      return PATTERN_STRING;
   }
}
