package org.radargun.stages.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;

public class ParseHelper {
	private ParseHelper() {
	}

	public static Set<Integer> parseSet(String str, String property, Log log) {
   	Set<Integer> s = new HashSet<Integer>();
      parseTo(str, property, log, s);
      return s;
	}
	
	public static List<Integer> parseList(String str, String property, Log log) {
      List<Integer> l = new ArrayList<Integer>();
      parseTo(str, property, log, l);
      return l;
   }
	
	private static void parseTo(String str, String property, Log log, Collection<Integer> collection) {
      try {
         int pos = 0;
         int lastNumber = -1;
         boolean addRange = false;
         boolean cont = true;
         do {
            int nextComma = str.indexOf(",", pos);
            if (nextComma < 0) nextComma = Integer.MAX_VALUE;
            int nextDotDot = str.indexOf("..", pos);
            if (nextDotDot < 0) nextDotDot = Integer.MAX_VALUE;
            int nextSeparator = Math.min(nextComma, nextDotDot);
            int number;
            if (nextSeparator == Integer.MAX_VALUE) {
               number = Integer.parseInt(str.substring(pos).trim());               
            } else {
               number = Integer.parseInt(str.substring(pos, nextSeparator).trim());
            }
            if (addRange) {
               for (int i = lastNumber + 1; i <= number; ++i) {
                  collection.add(i);
               }
            } else {
               collection.add(number);
            }
            if (nextSeparator == nextDotDot) {
               addRange = true;
               lastNumber = number;
               pos = nextDotDot + 2;
            } else {
               addRange = false;
               pos = nextComma + 1;
            }
         } while (cont);         
      } catch (NumberFormatException e) {
         log.error("Failed to parse " + property + "=" + str);
      }      
	}
	
	public static String toString(Collection<Integer> set) {
	   if (set == null) throw new IllegalArgumentException("null");
	   StringBuilder sb = new StringBuilder();
	   Iterator<Integer> it = set.iterator();
	   sb.append('[');
	   if (it.hasNext()) {
   	   while (true) {
   	      sb.append(it.next());
   	      if (it.hasNext()) sb.append(", ");
   	      else break;
   	   }
	   }
	   sb.append(']');
	   return sb.toString();
	}
	
	public static String toString(Set<Integer> set, String ifNull) {
      if (set == null) return ifNull;       
      return toString(set);
   }   
}
