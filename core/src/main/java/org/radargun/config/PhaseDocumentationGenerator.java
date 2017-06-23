package org.radargun.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.radargun.Stage;

/**
 * This is used to generate the stages/properties into documentation file
 *
 * @author Zdenek Hostasa &lt;zhostasa@redhat.com&gt; 
 */
public final class PhaseDocumentationGenerator {

   private static String eol = System.getProperty("line.separator");

   private PhaseDocumentationGenerator() {
   }

   public static void main(String[] args) {
      if (args[0] == null || args[0].isEmpty()) {
         System.out.println("No path for files provided");
         return;
      }

      String path = args[0];

      for (Map.Entry<String, Map<String, Class<? extends Stage>>> pair : StageHelper.getStages().entrySet()) {
         String namespace = pair.getKey();

         String[] splits = namespace.split(":");
         String extension = splits[splits.length - 2];

         File file = new File(path + extension + ".md");

         try (FileWriter fw = new FileWriter(file)) {

            fw.write(makeHeader(extension, namespace));

            for (Map.Entry<String, Class<? extends Stage>> entry : pair.getValue().entrySet()) {
               if (Modifier.isAbstract(entry.getValue().getModifiers()))
                  continue;
               fw.write("### " + entry.getKey() + eol);
               fw.write(entry.getValue().getAnnotation(org.radargun.config.Stage.class).doc() + eol);
               for (Map.Entry<String, Path> property : PropertyHelper.getProperties(entry.getValue(), true, false, false).entrySet()) {
                  Property propertyAnnotation = property.getValue().getTargetAnnotation();
                  if (propertyAnnotation.readonly())
                     continue;
                  fw.write("> " + property.getKey() + (propertyAnnotation.optional() ? " (**optional**)" : " (**mandatory**)") + " - "
                        + propertyAnnotation.doc() + "  " + eol);
               }
               fw.write(eol);
            }

         } catch (FileNotFoundException e) {
            e.printStackTrace();
         } catch (IOException e) {
            e.printStackTrace();
         }

      }
   }

   private static String makeHeader(String extension, String namespace) {
      StringBuilder buf = new StringBuilder();

      // Top marker for jekyll
      buf.append("---" + eol + "---" + eol + eol);

      String header = Character.toUpperCase(extension.charAt(0)) + extension.substring(1) + " stages" + eol;

      buf.append(header);

      for (int i = 1; i < header.length(); i++)
         buf.append('-');

      buf.append(eol + eol + "#### " + namespace + eol + eol);

      return buf.toString();

   }
}
