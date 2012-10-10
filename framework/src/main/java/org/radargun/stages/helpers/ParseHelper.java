package org.radargun.stages.helpers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;

public class ParseHelper {
	private ParseHelper() {
	}

	public static Set<Integer> parseSet(String set, String property, Log log) {
   	Set<Integer> s = new HashSet<Integer>();
      StringTokenizer tokenizer = new StringTokenizer(set, ",");
      try {
         while (tokenizer.hasMoreTokens()) {
            s.add(Integer.parseInt(tokenizer.nextToken().trim()));
         }
      } catch (NumberFormatException e) {
         log.error("Failed to parse " + property + "=" + set);
      }
      return s;
	}
	
	public static String toString(Set<Integer> set) {
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
