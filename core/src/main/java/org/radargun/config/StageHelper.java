package org.radargun.config;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Utils;

/**
 * Instantiates stages based on annotations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 12/17/12
 */
public class StageHelper {

   private static Log log = LogFactory.getLog(StageHelper.class);
   private static Map<String, Class<? extends org.radargun.Stage>> stagesDashed;

   static {
      stagesDashed = new HashMap<String, Class<? extends org.radargun.Stage>>();
      File libDir = new File("lib");
      if (libDir.exists() && libDir.isDirectory()) {
         for (File jar : libDir.listFiles(new Utils.JarFilenameFilter())) {
            stagesDashed.putAll(getStagesFromJar(jar.getPath(), true));
         }
      }
   }

   public static Map<String, Class<? extends org.radargun.Stage>> getStagesFromJar(String path, boolean dashNames) {
      System.err.println("Loading JARS from " + path);
      List<Class<? extends org.radargun.Stage>> list = AnnotatedHelper.getClassesFromJar(path, org.radargun.Stage.class, Stage.class);
      Map<String, Class<? extends org.radargun.Stage>> stages = new TreeMap<String, Class<? extends org.radargun.Stage>>();
      for (Class<? extends org.radargun.Stage> clazz : list) {
         Stage annotation = clazz.getAnnotation(Stage.class);
         String name;
         if (!annotation.name().equals(Stage.CLASS_NAME_WITHOUT_STAGE)) {
            name = annotation.name();
         } else {
            if (!clazz.getSimpleName().endsWith("Stage")) {
               log.error("Stage does not keep the conventional name *Stage");
               continue;
            }
            name = clazz.getSimpleName().substring(0, clazz.getSimpleName().length() - 5);
         }
         if (dashNames) {
            name = XmlHelper.camelCaseToDash(name);
         }
         stages.put(name, clazz);
         if (!annotation.deprecatedName().equals(Stage.NO_DEPRECATED_NAME)) {
            stages.put(annotation.deprecatedName(), clazz);
         }
      }
      return stages;
   }

   public static Class<? extends org.radargun.Stage> getStageClassByDashedName(String stageName) {
      Class<? extends org.radargun.Stage> clazz = stagesDashed.get(stageName);
      if (clazz != null) {
         return clazz;
      }
      log.warn("Stage " + stageName + " not registered, trying to find according to class name.");
      if (stageName.indexOf('.') < 0) {
         stageName = "org.radargun.stages." + stageName + "Stage";
      } else {
         stageName = XmlHelper.dashToCamelCase(stageName, true) + "Stage";
      }
      try {
         return (Class<? extends org.radargun.Stage>) Class.forName(stageName);
      } catch (ClassNotFoundException e) {
         String s = "Could not find stage class " + stageName;
         log.error(s);
         throw new RuntimeException(s, e);
      } catch (ClassCastException e) {
         String s = "Class " + stageName + " is not a stage!";
         log.error(s);
         throw new RuntimeException(s, e);
      }
   }

   public static String toString(org.radargun.Stage stage) {
      StringBuilder sb = new StringBuilder();
      sb.append(getStageName(stage.getClass())).append(" {");
      Set<Map.Entry<String, Path>> properties = PropertyHelper.getProperties(stage.getClass(), false).entrySet();

      for (Iterator<Map.Entry<String, Path>> iterator = properties.iterator(); iterator.hasNext(); ) {
         Map.Entry<String, Path> property = iterator.next();
         String propertyName = property.getKey();
         Path path = property.getValue();
         sb.append(propertyName).append('=');
         sb.append(PropertyHelper.getPropertyString(path, stage));
         if (iterator.hasNext()) {
            sb.append(", ");
         }
      }
      return sb.append(" }").toString();
   }

   public static boolean isStage(Class<?> stageClass) {
      return stageClass != null && org.radargun.Stage.class.isAssignableFrom(stageClass)
            && stageClass.isAnnotationPresent(Stage.class);
   }

   public static String getStageName(Class<? extends org.radargun.Stage> clazz) {
      if (clazz == null) throw new IllegalArgumentException("Class cannot be null");
      Stage stageAnnotation = (Stage)clazz.getAnnotation(Stage.class);
      if (stageAnnotation == null) throw new IllegalArgumentException(clazz + " is not properly annotated.");
      if (!stageAnnotation.name().equals(Stage.CLASS_NAME_WITHOUT_STAGE)) return stageAnnotation.name();
      String name = clazz.getSimpleName();
      if (!name.endsWith("Stage")) throw new IllegalArgumentException(clazz.getCanonicalName());
      return name.substring(0, name.length() - 5);
   }
}
