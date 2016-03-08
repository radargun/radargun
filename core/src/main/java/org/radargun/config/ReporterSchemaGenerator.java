package org.radargun.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.radargun.Version;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.ReporterHelper;

/**
 * Generates schemas for reporters
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ReporterSchemaGenerator extends SchemaGenerator {
   protected static final String NAMESPACE_ROOT = "urn:radargun:reporters:";
   protected final String reporterModule;
   protected final Map<String, Class<? extends Reporter>> reporters;

   public ReporterSchemaGenerator(String reporterModule, Map<String, Class<? extends Reporter>> reporters) {
      super(NAMESPACE_ROOT, String.format(NAMESPACE_ROOT + "%s:%s", reporterModule, Version.SCHEMA_VERSION), "reporter-");
      this.reporterModule = reporterModule;
      this.reporters = reporters;
   }

   @Override
   protected String findDocumentation(Class<?> clazz) {
      return null;
   }

   @Override
   protected void generate() {
      createSchemaElement(namespace);
      for (Map.Entry<String, Class<? extends Reporter>> reporter : reporters.entrySet()) {
         XmlType type = generateClass(reporter.getValue());
         createReference(schema, reporter.getKey(), type.toString());
      }
   }

   public static void main(String[] args) {
      if (args.length < 3 || args[0] == null || args[1] == null || args[2] == null)
         throw new IllegalArgumentException("No schema location directory specified!" + args);

      String schemaDirectory = args[0];
      String reporterDirectory = args[1];
      String reporterName = args[2];

      Map<String, Class<? extends Reporter>> reporters = new HashMap<>();
      ReporterHelper.loadReporters(new File(reporterDirectory), reporters);
      ReporterSchemaGenerator generator = new ReporterSchemaGenerator(reporterName, reporters);
      generator.generate(schemaDirectory, String.format("%s-%s.xsd", reporterName, Version.SCHEMA_VERSION));
   }
}
