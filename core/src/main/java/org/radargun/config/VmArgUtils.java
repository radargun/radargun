package org.radargun.config;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class VmArgUtils {

   private VmArgUtils() {
   }

   static void ensureArg(Collection<String> args, String arg) {
      if (!args.contains(arg))
         args.add(arg);
   }

   static void replace(List<String> args, String prefix, String value) {
      for (Iterator<String> it = args.iterator(); it.hasNext();) {
         String arg = it.next();
         if (arg.startsWith(prefix)) {
            it.remove();
         }
      }
      args.add(prefix + value);
   }
}
