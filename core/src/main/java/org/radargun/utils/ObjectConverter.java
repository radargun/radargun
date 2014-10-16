package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.config.Converter;

/**
* Converts strings, booleans and number to instance of correct type (according to the prefix).
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
public class ObjectConverter implements Converter<Object> {
   private static final NumberConverter NUMBER_CONVERTER = new NumberConverter();
   private static final Pattern STRING_PATTERN = Pattern.compile("string (.*)");
   private static final Pattern BOOLEAN_PATTERN = Pattern.compile("true|false");

   @Override
   public Object convert(String string, Type type) {
      Matcher m;
      if ((m = STRING_PATTERN.matcher(string)).matches()) {
         return m.group(1);
      } else if ((m = BOOLEAN_PATTERN.matcher(string)).matches()) {
         return Boolean.parseBoolean(string);
      }
      Number n = NUMBER_CONVERTER.toNumber(string);
      if (n != null) return n;
      return string;
   }

   @Override
   public String convertToString(Object value) {
      return String.valueOf(value);
   }

   @Override
   public String allowedPattern(Type type) {
      return "string .*|true|false|" + NUMBER_CONVERTER.allowedPattern(type);
   }
}
