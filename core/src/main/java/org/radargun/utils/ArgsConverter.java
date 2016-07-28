package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.radargun.config.Converter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Tokenizes the arguments into list, parsing text in apostrophes.
 * Apostrophes cannot be escaped.
 */
public class ArgsConverter implements Converter<List<String>> {
   private static Log log = LogFactory.getLog(ArgsConverter.class);

   @Override
   public List<String> convert(String string, Type type) {
      ArrayList<String> list = new ArrayList<String>();
      Tokenizer tokenizer = new Tokenizer(string, new String[] {" ", "\t", "\n", "\r", "\f", "'"}, true, false, 0);
      StringBuilder sb = null;
      while (tokenizer.hasMoreTokens()) {
         String token = tokenizer.nextToken();
         if (token.charAt(0) == '\'') {
            if (sb == null) { // non-quoted
               sb = new StringBuilder().append(token);
            } else { // quoted
               sb.append("'");
               list.add(sb.toString());
               sb = null;
            }
         } else if (Character.isWhitespace(token.charAt(0)) && token.length() == 1) {
            if (sb != null) {
               sb.append(token);
            }
         } else {
            if (sb == null) {
               list.add(token);
            } else {
               sb.append(token);
            }
         }
      }
      if (sb != null) {
         log.warn("Args are not closed: " + string);
         sb.append('\'');
         list.add(sb.toString());
      }
      return list;
   }

   @Override
   public String convertToString(List<String> value) {
      if (value == null) {
         return "<none>";
      }
      StringBuilder sb = new StringBuilder();
      for (String arg : value) {
         sb.append(arg).append(' ');
      }
      return sb.toString();
   }

   @Override
   public String allowedPattern(Type type) {
      return ANY_MULTI_LINE;
   }
}
