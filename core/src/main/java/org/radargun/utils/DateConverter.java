package org.radargun.utils;

import java.lang.reflect.Type;
import java.util.Date;

import org.radargun.config.Converter;

/**
 * Converts digit string into Date object and vice versa
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class DateConverter implements Converter<Date> {

   @Override
   public Date convert(String string, Type type) {
      return new Date(Long.parseLong(string));
   }

   @Override
   public String convertToString(Date value) {
      return String.valueOf(value.getTime());
   }

   @Override
   public String allowedPattern(Type type) {
      return "[0-9]+";
   }
}
