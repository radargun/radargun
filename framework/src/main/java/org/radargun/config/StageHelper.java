package org.radargun.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Instantiates stages based on annotations
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 12/17/12
 */
public class StageHelper {

   private static Log log = LogFactory.getLog(StageHelper.class);
   private static Map<String, Class<? extends org.radargun.Stage>> stages;

   static {
      URL jarFile = StageHelper.class.getProtectionDomain().getCodeSource().getLocation();
      stages = getStagesFromJar(jarFile.getPath());
   }

   public static Map<String, Class<? extends org.radargun.Stage>> getStagesFromJar(String path) {
      System.err.println("Loading JARS from " + path);
      Map<String, Class<? extends org.radargun.Stage>> stages = new HashMap<String, Class<? extends org.radargun.Stage>>();
      try {
         ZipInputStream inputStream = new ZipInputStream(new FileInputStream(path));
         for(;;) {
            ZipEntry entry = inputStream.getNextEntry();
            if (entry == null) break;
            if (!entry.getName().endsWith(".class")) continue;
            String className = entry.getName().replace('/', '.').substring(0, entry.getName().length() - 6);
            try {
               Class<?> clazz = Class.forName(className);
               Stage stageAnnotation = clazz.getAnnotation(Stage.class);
               if (stageAnnotation != null) {
                  if (!org.radargun.Stage.class.isAssignableFrom(clazz)) {
                     log.warn("Non-stage marked as stage: " + clazz.getName());
                     continue;
                  }
                  Class<? extends org.radargun.Stage> stageClass = (Class<? extends org.radargun.Stage>) clazz;
                  String stageName;
                  if (!stageAnnotation.name().equals(Stage.CLASS_NAME_WITHOUT_STAGE)) {
                     stageName = stageAnnotation.name();
                  } else {
                     if (!stageClass.getSimpleName().endsWith("Stage")) {
                        log.error("Stage does not keep the conventional name *Stage");
                        continue;
                     }
                     stageName = stageClass.getSimpleName().substring(0, stageClass.getSimpleName().length() - 5);
                  }
                  stages.put(stageName, stageClass);
                  if (!stageAnnotation.deprecatedName().equals(Stage.NO_DEPRECATED_NAME)) {
                     stages.put(stageAnnotation.deprecatedName(), stageClass);
                  }
               }
            } catch (ClassNotFoundException e) {
               log.warn("Cannot instantiate class " + className);
            }
         }
      } catch (FileNotFoundException e) {
         log.error("Cannot load executed JAR file '" + path + "'to find stages.");
      } catch (IOException e) {
         log.error("Cannot open/read JAR '" + path + "'");
      }
      return stages;
   }

   public static org.radargun.Stage getStage(String stageName) {
      Class<? extends org.radargun.Stage> clazz = stages.get(stageName);
      if (clazz == null) {
         log.warn("Stage " + stageName + " not registered, trying to find according to class name.");
         if (stageName.indexOf('.') < 0) {
            stageName = "org.radargun.stages." + stageName + "Stage";
         } else {
            stageName = stageName + "Stage";
         }
         try {
            clazz = (Class<? extends org.radargun.Stage>) Class.forName(stageName);
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
      try {
         return clazz.newInstance();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public static String toString(org.radargun.Stage stage) {
      StringBuilder sb = new StringBuilder();
      sb.append(getStageName(stage.getClass())).append(" {");
      Set<Map.Entry<String, Field>> properties = PropertyHelper.getProperties(stage.getClass()).entrySet();

      for (Iterator<Map.Entry<String,Field>> iterator = properties.iterator(); iterator.hasNext(); ) {
         Map.Entry<String, Field> property = iterator.next();
         String propertyName = property.getKey();
         Field propertyField = property.getValue();
         sb.append(propertyName).append('=');

         propertyField.setAccessible(true);
         Object value = null;
         try {
            value = propertyField.get(stage);
            Converter converter = propertyField.getAnnotation(Property.class).converter().newInstance();
            sb.append(converter.convertToString(value));
         } catch (IllegalAccessException e) {
            sb.append("<not accessible>");
         } catch (InstantiationException e) {
            sb.append("<cannot create converter: ").append(value).append(">");
         } catch (ClassCastException e) {
            sb.append("<cannot convert: ").append(value).append(">");
         } catch (Throwable t) {
            sb.append("<error ").append(t).append(": ").append(value).append(">");
         }
         if (iterator.hasNext()) {
            sb.append(", ");
         }
      }
      return sb.append(" }").toString();
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
