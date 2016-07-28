package org.radargun.stages.query;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class OrderBy {
   public final String attribute;
   public final boolean asc;

   public OrderBy(String attribute, boolean asc) {
      this.attribute = attribute;
      this.asc = asc;
   }

   public static class ListConverter implements org.radargun.config.Converter<List<OrderBy>> {
      @Override
      public List<OrderBy> convert(String string, Type type) {
         String[] parts = string.split(",", 0);
         ArrayList<OrderBy> result = new ArrayList<OrderBy>(parts.length);
         for (String part : parts) {
            int colon = part.indexOf(':');
            if (colon < 0) {
               result.add(new OrderBy(part.trim(), true));
            } else {
               String order = part.substring(colon + 1).trim();
               boolean asc;
               if (order.equalsIgnoreCase("ASC")) {
                  asc = true;
               } else if (order.equalsIgnoreCase("DESC")) {
                  asc = false;
               } else {
                  throw new IllegalArgumentException("Sort order: " + order);
               }
               result.add(new OrderBy(part.substring(0, colon).trim(), asc));
            }
         }
         return result;
      }

      @Override
      public String convertToString(List<OrderBy> value) {
         if (value == null) return "<unordered>";
         StringBuilder sb = new StringBuilder();
         for (OrderBy e : value) {
            sb.append(e.attribute).append(':').append(e.asc ? "ASC" : "DESC").append(", ");
         }
         return sb.substring(0, sb.length() - 2);
      }

      @Override
      public String allowedPattern(Type type) {
         return "[0-9a-zA-Z_]*(:ASC|:DESC)?(,\\s*[0-9a-zA-Z_]*(:ASC|:DESC)?)*";
      }
   }
}
