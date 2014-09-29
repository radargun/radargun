package org.radargun.config;

import java.lang.reflect.Modifier;
import java.util.Map;

import org.radargun.Stage;

/**
 * This is used to generate the stages/properties with GitHub wiki markup
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ConfigWikiGenerator {
   public static void main(String[] args) {
      for (Map.Entry<String, Class<? extends Stage>> entry : StageHelper.getStagesFromJar(args[0], true).entrySet()) {
         if (Modifier.isAbstract(entry.getValue().getModifiers())) continue;
         System.out.println("## " + entry.getKey());
         System.out.println(entry.getValue().getAnnotation(org.radargun.config.Stage.class).doc());
         for (Map.Entry<String, Path> property : PropertyHelper.getProperties(entry.getValue(), true, false, false).entrySet()) {
            Property propertyAnnotation = property.getValue().getTargetAnnotation();
            if (propertyAnnotation.readonly()) continue;
            System.out.println("* " + property.getKey()
                                     + (propertyAnnotation.optional() ? " [optional]" : " [mandatory]") + ": " + propertyAnnotation.doc());
         }
         System.out.println();
      }
   }
}
