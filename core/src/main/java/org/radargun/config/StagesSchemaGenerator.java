package org.radargun.config;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.*;

import org.radargun.utils.Utils;
import org.w3c.dom.Element;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class StagesSchemaGenerator extends SchemaGenerator {
   private final Collection<Class<? extends org.radargun.Stage>> stages;
   private final Collection<Class<?>> extraClasses;

   public StagesSchemaGenerator(String namespace, Collection<Class<? extends org.radargun.Stage>> stages, Collection<Class<?>> extraClasses) {
      super(StageHelper.NAMESPACE_ROOT, namespace, "radargun-");
      this.stages = stages;
      this.extraClasses = extraClasses;
   }

   @Override
   protected String findDocumentation(Class<?> clazz) {
      org.radargun.config.Stage stageAnnotation = clazz.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation != null) return stageAnnotation.doc();
      return null;
   }

   @Override
   protected void generate() {
      schema = createSchemaElement(namespace);
      Set<Class<? extends org.radargun.Stage>> generatedStages = new HashSet<>();
      for (Class<? extends org.radargun.Stage> stage : stages) {
         generateStage(schema, stage, generatedStages);
      }
      for (Class<?> extra : extraClasses) {
         generateClass(extra);
      }
   }

   void generateStage(Element parent, Class stage, Set<Class<? extends org.radargun.Stage>> generatedStages) {
      if (generatedStages.contains(stage)) return;
      NamespaceHelper.Coords coords = NamespaceHelper.getCoords(namespaceRoot, stage, omitPrefix);
      if (coords != null && !coords.namespace.equals(this.namespace)) {
         requireImport(coords.namespace);
         return;
      }
//      boolean hasParentStage = org.radargun.Stage.class.isAssignableFrom(stage.getSuperclass());
//      if (hasParentStage) {
//         generateStage(parent, stage.getSuperclass(), generatedStages);
//      }
      org.radargun.config.Stage stageAnnotation = (org.radargun.config.Stage) stage.getAnnotation(org.radargun.config.Stage.class);
      if (stageAnnotation == null) return; // not a proper stage

      XmlType stageType = generateClass(stage);
      if (parent != null && !Modifier.isAbstract(stage.getModifiers()) && !stageAnnotation.internal()) {
         createReference(parent, XmlHelper.camelCaseToDash(StageHelper.getStageName(stage)), stageType.toString());
         if (!stageAnnotation.deprecatedName().equals(org.radargun.config.Stage.NO_DEPRECATED_NAME)) {
            createReference(parent, XmlHelper.camelCaseToDash(stageAnnotation.deprecatedName()), stageType.toString());
         }
      }
      generatedStages.add(stage);
   }

   /**
    * Generate the XSD files for stages. First argument is directory where the XSD files should be placed.
    */
   public static void main(String[] args) {
      if (args.length < 1 || args[0] == null)
         throw new IllegalArgumentException("No schema location directory specified!");
      // We have to ensure that all definition elements are defined in given namespace in case these would
      // be referenced from another namespace
      Map<String, List<Class<?>>> definitions = new HashMap<>();
      ClasspathScanner.scanClasspath(null, DefinitionElement.class, "org.radargun",
         clazz -> indexClass(definitions, clazz));
      Set<String> allNamespaces = new HashSet<>(definitions.keySet());
      ClasspathScanner.scanClasspath(null, EnsureInSchema.class, "org.radargun",
         clazz -> indexClass(definitions, clazz));
      allNamespaces.addAll(StageHelper.getStages().keySet());
      for (String namespace : allNamespaces) {
         Map<String, Class<? extends org.radargun.Stage>> stages = StageHelper.getStages().get(namespace);
         List<Class<?>> extraClasses = definitions.get(namespace);
         if (stages == null) {
            stages = Collections.EMPTY_MAP;
         }
         if (extraClasses == null) {
            extraClasses = Collections.EMPTY_LIST;
         }
         new StagesSchemaGenerator(namespace, stages.values(), extraClasses).generate(args[0], NamespaceHelper.getJarMajorMinor(namespace) + ".xsd");
      }
   }

   protected static void indexClass(Map<String, List<Class<?>>> definitions, Class<?> clazz) {
      NamespaceHelper.Coords coords = NamespaceHelper.suggestCoordinates(StageHelper.NAMESPACE_ROOT, clazz, "radargun-");
      File[] codepaths = coords.explicit ? null : new File[] {new File(Utils.getCodePath(clazz))};
      NamespaceHelper.registerNamespace(coords.namespace, codepaths, coords.jarMajorMinor);
      List<Class<?>> byNamespace = definitions.get(coords.namespace);
      if (byNamespace == null) {
         definitions.put(coords.namespace, byNamespace = new ArrayList<>());
      }
      byNamespace.add(clazz);
   }

}
