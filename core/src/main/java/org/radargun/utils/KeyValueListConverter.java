package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;

import org.radargun.config.Converter;

/**
 * Parses string containing key-value pairs into Map and vice versa. Individual pairs are separated by ';'.
 * Keys and values are separated by ':'.
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class KeyValueListConverter implements Converter<Map<String, String>> {

   @Override
   public Map<String, String> convert(String string, Type type) {
      return Utils.parseParams(string);
   }

   @Override
   public String convertToString(Map<String, String> value) {
      StringBuilder kvBuilder = new StringBuilder();
      Iterator<Map.Entry<String, String>> it = value.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<String, String> pair = it.next();
         kvBuilder.append(pair.getKey())
            .append(":")
            .append(pair.getValue());
         if (it.hasNext()) {
            kvBuilder.append(";");
         }
      }
      return kvBuilder.toString();
   }

   @Override
   public String allowedPattern(Type type) {
      return "[.+:.+;]*.+:.+";
   }
}
