package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.Arrays;

import org.radargun.config.Converter;

/**
 * Converts string separated by comma to an array of strings
 */
public class StringArrayConverter implements Converter<String[]> {
   @Override
   public String[] convert(String string, Type ignored) {
      String[] data = string.split(",");
      String[] values = new String[data.length];
      for (int i = 0; i < data.length; i++) {
         values[i] = data[i];
      }
      return values;
   }

   @Override
   public String convertToString(String[] value) {
      return Arrays.toString(value);
   }

   @Override
   public String allowedPattern(Type type) {
      return "^[A-Z,a-z,0-9,]*$";
   }
}
