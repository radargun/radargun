package org.radargun.config;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
      stages = getStagesFromJar(AnnotatedHelper.getJAR(StageHelper.class).getPath());
   }

   public static Map<String, Class<? extends org.radargun.Stage>> getStagesFromJar(String path) {
      System.err.println("Loading JARS from " + path);
      List<Class<? extends org.radargun.Stage>> list = AnnotatedHelper.getClassesFromJar(path, org.radargun.Stage.class, Stage.class);
      Map<String, Class<? extends org.radargun.Stage>> stages = new HashMap<String, Class<? extends org.radargun.Stage>>();
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
         stages.put(name, clazz);
         if (!annotation.deprecatedName().equals(Stage.NO_DEPRECATED_NAME)) {
            stages.put(annotation.deprecatedName(), clazz);
         }
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
