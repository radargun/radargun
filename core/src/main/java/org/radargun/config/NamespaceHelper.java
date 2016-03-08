package org.radargun.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.utils.Utils;

/**
 * Helper code for namespace-relate stuff.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class NamespaceHelper {
   private static Map<String, String> namespaceToJarMajorMinor = new HashMap<>();
   private static Map<File, String> codepathToToJarMajorMinor = new HashMap<>();

   private NamespaceHelper() {}

   public static void registerNamespace(String namespace, File[] codepaths, String jarMinorMajor) {
      String prev = namespaceToJarMajorMinor.put(namespace, jarMinorMajor);
      if (prev != null && !prev.equals(jarMinorMajor)) {
         throw new IllegalArgumentException("Conflict for " + namespace + ", previous=" + prev + ", new=" + jarMinorMajor);
      }
      if (codepaths != null) {
         for (File codepath : codepaths) {
            prev = codepathToToJarMajorMinor.put(codepath, jarMinorMajor);
            if (prev != null && !prev.equals(jarMinorMajor)) {
               throw new IllegalArgumentException("Conflict for " + codepath + ", previous=" + prev + ", new=" + jarMinorMajor);
            }
         }
      }
   }

   private static Coords getCoords(String namespaceRoot, Namespace ns) {
      if (!ns.value().startsWith(namespaceRoot)) {
         throw new IllegalStateException("Namespace should start with '" + namespaceRoot + "', malformed namespace '" + ns.value() + "'");
      }
      return new Coords(ns.value(), ns.value().substring(namespaceRoot.length()).replaceAll(":", "-"), true);
   }

   public static Coords getCoords(String namespaceRoot, Class<?> clazz, String omitPrefix) {
      Namespace ns = clazz.getAnnotation(Namespace.class);
      if (ns != null) {
         return getCoords(namespaceRoot, ns);
      } else {
         String jarMajorMinor = codepathToToJarMajorMinor.get(new File(Utils.getCodePath(clazz)));
         return jarMajorMinor == null ? null : new Coords(getNamespace(namespaceRoot, jarMajorMinor, omitPrefix), jarMajorMinor, false);
      }
   }

   public static Coords suggestCoordinates(String namespaceRoot, Class<?> clazz, String omitPrefix) {
      Namespace ns = clazz.getAnnotation(Namespace.class);
      if (ns != null) {
         return getCoords(namespaceRoot, ns);
      } else {
         File jar = new File(Utils.getCodePath(clazz));
         String filename = jar.toPath().getFileName().toString();
         Matcher matcher = Pattern.compile("(.*[0-9]+\\.[0-9]+)\\.[0-9]+.*").matcher(filename);
         if (!matcher.matches()) {
            throw new IllegalStateException("Filename does not match to the expected pattern: " + filename);
         }
         String jarMajorMinor = matcher.group(1);

         String namespace = getNamespace(namespaceRoot, jarMajorMinor, omitPrefix);
         return new Coords(namespace, jarMajorMinor, false);
      }
   }

   private static String getNamespace(String namespaceRoot, String jarMajorMinor, String omitPrefix) {
      int lastDash = jarMajorMinor.lastIndexOf("-");
      int prefix = jarMajorMinor.startsWith(omitPrefix) ? omitPrefix.length() : 0;
      String shortNs = jarMajorMinor;
      if (lastDash >= 0) {
         shortNs = jarMajorMinor.substring(prefix, lastDash) + ":" + jarMajorMinor.substring(lastDash + 1);
      } else if (prefix > 0) {
         shortNs = jarMajorMinor.substring(prefix);
      }
      return namespaceRoot + shortNs;
   }

   public static String getJarMajorMinor(String namespace) {
      return namespaceToJarMajorMinor.get(namespace);
   }

   public static class Coords {
      public final String namespace;
      public final String jarMajorMinor;
      public final boolean explicit;

      public Coords(String namespace, String jarMajorMinor, boolean explicit) {
         this.namespace = namespace;
         this.jarMajorMinor = jarMajorMinor;
         this.explicit = explicit;
      }
   }
}
