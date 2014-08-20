package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.config.Converter;

/**
* Converts numbers to instance of correct type (according to the prefix).
*
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
public class NumberConverter implements Converter<Number> {
   private static final Pattern INT_PATTERN = Pattern.compile("int (.*)");
   private static final Pattern LONG_PATTERN = Pattern.compile("long (.*)");
   private static final Pattern FLOAT_PATTERN = Pattern.compile("float (.*)");
   private static final Pattern DOUBLE_PATTERN = Pattern.compile("double (.*)");
   @Override
   public Number convert(String string, Type type) {
      Number n = toNumber(string);
      if (n != null) return n;
      throw new IllegalArgumentException("Cannot parse " + string);
   }

   public Number toNumber(String string) {
      Matcher m;
      if ((m = INT_PATTERN.matcher(string)).matches()) {
         return Integer.parseInt(m.group(1));
      } else if ((m = LONG_PATTERN.matcher(string)).matches()) {
         return Long.parseLong(m.group(1));
      } else if ((m = FLOAT_PATTERN.matcher(string)).matches()) {
         return Float.parseFloat(m.group(1));
      } else if ((m = DOUBLE_PATTERN.matcher(string)).matches()) {
         return Double.parseDouble(m.group(1));
      }
      try {
         long l = Long.parseLong(string);
         if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return (int) l;
         }
      } catch (NumberFormatException e) {}
      try {
         return Double.parseDouble(string);
      } catch (NumberFormatException e) {}
      return null;
   }

   @Override
   public String convertToString(Number value) {
      return String.valueOf(value);
   }

   @Override
   public String allowedPattern(Type type) {
      return "(int |long |float |double )?(\\+|-)?[0-9.]*";
   }
}
