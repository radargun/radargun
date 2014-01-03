package org.radargun.config;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.CacheWrapperStressor;

/**
 * Helper for loading the CacheWrapperStressors
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 2/19/13
 */
public class StressorHelper {

   private static Log log = LogFactory.getLog(StressorHelper.class);
   private static Map<String, Class<? extends CacheWrapperStressor>> stressors;

   static {
      List<Class<? extends CacheWrapperStressor>> list
            = AnnotatedHelper.getClassesFromJar(AnnotatedHelper.getJAR(StressorHelper.class).getPath(), CacheWrapperStressor.class, Stressor.class);
      stressors = new TreeMap<String, Class<? extends CacheWrapperStressor>>();
      for (Class<? extends CacheWrapperStressor> clazz : list) {
         Stressor annotation = clazz.getAnnotation(Stressor.class);
         String stageName;
         if (!annotation.name().equals(Stressor.CLASS_NAME_WITHOUT_STRESSOR)) {
            stageName = annotation.name();
         } else {
            if (!clazz.getSimpleName().endsWith("Stressor")) {
               log.error("Stressor does not keep the conventional name *Stressor");
               continue;
            }
            stageName = clazz.getSimpleName().substring(0, clazz.getSimpleName().length() - 8);
         }
         stressors.put(stageName, clazz);
         if (!annotation.deprecatedName().equals(Stage.NO_DEPRECATED_NAME)) {
            stressors.put(annotation.deprecatedName(), clazz);
         }
      }
   }

   public static CacheWrapperStressor getStressor(String stressorName) {
      Class<? extends CacheWrapperStressor> clazz = stressors.get(stressorName);
      if (clazz == null) {
         log.warn("Stressor " + stressorName + " not registered, trying to find according to class name.");
         if (stressorName.indexOf('.') < 0) {
            stressorName = "org.radargun.stressors." + stressorName + "Stressor";
         } else {
            stressorName = stressorName + "Stressor";
         }
         try {
            clazz = (Class<? extends CacheWrapperStressor>) Class.forName(stressorName);
         } catch (ClassNotFoundException e) {
            String s = "Could not find stressor class " + stressorName;
            log.error(s);
            throw new RuntimeException(s, e);
         } catch (ClassCastException e) {
            String s = "Class " + stressorName + " is not a stressor!";
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
}
