package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import org.radargun.config.Converter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

public class EnvsConverter implements Converter<Map<String, String>> {
   private static Log log = LogFactory.getLog(EnvsConverter.class);

   @Override
   public Map<String, String> convert(String string, Type type) {
      Map<String, String> env = new TreeMap<String, String>();
      String[] lines = string.split("\n");
      for (String line : lines) {
         int eqIndex = line.indexOf('=');
         if (eqIndex < 0) {
            if (line.trim().length() > 0) {
               log.warn("Cannot parse env " + line);
            }
         } else {
            env.put(line.substring(0, eqIndex).trim(), line.substring(eqIndex + 1).trim());
         }
      }
      return env;
   }

   @Override
   public String convertToString(Map<String, String> value) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> envVar : value.entrySet()) {
         sb.append(envVar.getKey()).append('=').append(envVar.getValue()).append('\n');
      }
      return sb.toString();
   }

   @Override
   public String allowedPattern(Type type) {
      return "([A-Z_]+[A-Z0-9_]*=.*(\n|\r)*)+";
   }
}