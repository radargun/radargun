package org.radargun.config;

import java.io.File;
import java.util.Map;

import org.radargun.Directories;
import org.radargun.Service;
import org.radargun.ServiceHelper;
import org.radargun.Version;
import org.radargun.utils.ArgsHolder;

/**
 * Generates schemas for reporters
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PluginSchemaGenerator extends SchemaGenerator {
   protected static final String NAMESPACE_ROOT = "urn:radargun:plugins:";
   protected final Map<String, Class<?>> services;

   public PluginSchemaGenerator(Map<String, Class<?>> services, String namespace) {
      super(NAMESPACE_ROOT, namespace, "plugin-");
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
      createSchemaElement(namespace);
      for (Map.Entry<String, Class<?>> reporter : services.entrySet()) {
         XmlType type = generateClass(reporter.getValue());
         createReference(schema, reporter.getKey(), type.toString());
      }
   }

   public static void main(String[] args) {
      if (args.length < 2 || args[0] == null || args[1] == null)
         throw new IllegalArgumentException("No schema location directory or plugin name specified!" + args);

      String schemaDirectory = args[0];
      String plugin = args[1];

      // register namespaces for all plugins
      for (File other : Directories.PLUGINS_DIR.listFiles()) {
         String pluginName = other.getName();
         if (pluginName.startsWith("plugin-")) {
            pluginName = pluginName.substring(7);
         }
         NamespaceHelper.registerNamespace(namespace(pluginName), other.listFiles(), pluginName + "-" + Version.SCHEMA_VERSION);
      }

      ArgsHolder.setCurrentPlugin(plugin);
      Map<String, Class<?>> services = ServiceHelper.loadServices(plugin);
      PluginSchemaGenerator generator = new PluginSchemaGenerator(services, namespace(plugin));
      generator.generate(schemaDirectory, String.format("%s-%s.xsd", plugin, Version.SCHEMA_VERSION));
      // explicitly shutdown if a dependency started non-daemon thread from static ctor
      System.exit(0);
   }

   protected static String namespace(String plugin) {
      return String.format(NAMESPACE_ROOT + "%s:%s", plugin, Version.SCHEMA_VERSION);
   }
}
