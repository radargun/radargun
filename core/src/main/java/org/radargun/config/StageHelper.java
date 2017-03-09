package org.radargun.config;

import java.io.File;
import java.util.*;

import org.radargun.utils.Utils;

/**
 * Instantiates stages based on annotations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class StageHelper {
   private static Map<String, SortedMap<String, Class<? extends org.radargun.Stage>>> stagesByNamespace = new HashMap<>();
   protected static final String NAMESPACE_ROOT = "urn:radargun:stages:";

   static {
      ClasspathScanner.scanClasspath(org.radargun.Stage.class, Stage.class, null, clazz -> {
         Stage annotation = clazz.getAnnotation(Stage.class);
         addStage(XmlHelper.camelCaseToDash(getStageName(clazz, annotation)), clazz);
         if (!annotation.deprecatedName().equals(Stage.NO_DEPRECATED_NAME)) {
            addStage(annotation.deprecatedName(), clazz);
         }
      });
   }

   private StageHelper() {}

   private static void addStage(String stageName, Class<? extends org.radargun.Stage> stageClazz) {
      NamespaceHelper.Coords coords = NamespaceHelper.suggestCoordinates(NAMESPACE_ROOT, stageClazz, "radargun-");
      File[] codepaths = new File[] {new File(Utils.getCodePath(stageClazz))};
      NamespaceHelper.registerNamespace(coords.namespace, codepaths, coords.jarMajorMinor);
      addStageToNamespace(coords.namespace, stageName, stageClazz);
      if (!coords.deprecatedNamespace.equals(Namespace.NO_DEPRECATED_NAME)) {
         NamespaceHelper.registerNamespace(coords.deprecatedNamespace, codepaths, coords.jarMajorMinor);
         addStageToNamespace(coords.deprecatedNamespace, stageName, stageClazz);
      }
   }

   private static void addStageToNamespace(String namespace, String stageName, Class<? extends org.radargun.Stage> stageClazz) {
      SortedMap<String, Class<? extends org.radargun.Stage>> stages = stagesByNamespace.get(namespace);
      if (stages == null) {
         stagesByNamespace.put(namespace, stages = new TreeMap<>());
      }
      stages.put(stageName, stageClazz);
   }

   public static Map<String, Map<String, Class<? extends org.radargun.Stage>>> getStages() {
      return Collections.unmodifiableMap(stagesByNamespace);
   }

   public static Class<? extends org.radargun.Stage> getStageClassByDashedName(String namespace, String stageName) {
      SortedMap<String, Class<? extends org.radargun.Stage>> stages = stagesByNamespace.get(namespace);
      if (stages == null) {
         throw new IllegalArgumentException("Namespace '" + namespace + "' not found. Available namespaces: " + stagesByNamespace.keySet());
      }
      Class<? extends org.radargun.Stage> clazz = stages.get(stageName);
      if (clazz != null) {
         return clazz;
      } else {
         throw new IllegalArgumentException("Could not find stage '" + stageName + "' in namespace '" + namespace + "'");
      }
   }

   public static String toString(org.radargun.Stage stage) {
      StringBuilder sb = new StringBuilder();
      sb.append(getStageName(stage.getClass())).append(PropertyHelper.toString(stage));
      return sb.toString();
   }

   public static boolean isStage(Class<?> stageClass) {
      return stageClass != null && org.radargun.Stage.class.isAssignableFrom(stageClass)
         && stageClass.isAnnotationPresent(Stage.class);
   }

   public static String getStageName(Class<? extends org.radargun.Stage> clazz) {
      if (clazz == null) throw new IllegalArgumentException("Class cannot be null");
      Stage annotation = clazz.getAnnotation(Stage.class);
      if (annotation == null) {
         throw new IllegalArgumentException(clazz + " is not properly annotated.");
      }
      return getStageName(clazz, annotation);
   }

   protected static String getStageName(Class<? extends org.radargun.Stage> clazz, Stage annotation) {
      String name;
      if (!annotation.name().equals(Stage.CLASS_NAME_WITHOUT_STAGE)) {
         name = annotation.name();
      } else {
         if (!clazz.getSimpleName().endsWith("Stage")) {
            throw new IllegalArgumentException(clazz.getName() + " does not keep the conventional name *Stage");
         }
         name = clazz.getSimpleName().substring(0, clazz.getSimpleName().length() - 5);
      }
      return name;
   }

}
