package org.radargun.config;

/**
 * Variation of AbstractConfigurationTest when scenario is imported from another benchmark file
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class ConfigurationWithImportedScenarioFromAnotherBenchmarkTest extends AbstractConfigurationTest {
   public ConfigurationWithImportedScenarioFromAnotherBenchmarkTest() {
      resources.add("benchmark-test.xml");
   }

   @Override
   protected String getBenchmark() {
      return "benchmark-importedScenarioFromAnotherBenchmark.xml";
   }
}
