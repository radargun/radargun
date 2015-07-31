package org.radargun.config;

import java.util.Map;

import org.radargun.Service;
import org.radargun.ServiceHelper;

/**
 * Generates schemas for reporters
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PluginSchemaGenerator extends SchemaGenerator {
   private static final String VERSION = "3.0"; // TODO: version plugins as plugin property
   protected final String plugin;
   protected final Map<String, Class<?>> services;

   public PluginSchemaGenerator(String plugin, Map<String, Class<?>> services) {
      this.plugin = plugin;
      this.services = services;
   }

   @Override
   protected String findDocumentation(Class<?> clazz) {
      Service service = clazz.getAnnotation(Service.class);
      if (service != null) {
         return service.doc();
      }
      return null;
   }

   @Override
   protected void generate() {
      createSchemaElement(String.format("radargun:plugins:%s:%s", plugin, VERSION));
      for (Map.Entry<String, Class<?>> reporter : services.entrySet()) {
         String type = generateClass(reporter.getValue());
         createReference(schema, reporter.getKey(), type);
      }
   }

   public static void main(String args[]) {
      if (args.length < 2 || args[0] == null || args[1] == null)
         throw new IllegalArgumentException("No schema location directory or plugin name specified!" + args);

      String schemaDirectory = args[0];
      String plugin = args[1];

      Map<String, Class<?>> services = ServiceHelper.loadServices(plugin);
      PluginSchemaGenerator generator = new PluginSchemaGenerator(plugin, services);
      generator.generate(schemaDirectory, String.format("%s-%s.xsd", plugin, VERSION));
   }
}
