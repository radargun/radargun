package org.radargun.config;

/**
 * Variation of AbstractConfigurationTest when scenario is imported from file that contains only scenario
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class ConfigurationWithImportedScenarioFromDesignatedScenarioFileTest extends AbstractConfigurationTest {

   public ConfigurationWithImportedScenarioFromDesignatedScenarioFileTest() {
      resources.add("designatedScenarioFile.xml");
   }

   @Override
   protected String getBenchmark() {
      return "benchmark-importedScenarioFromDesignatedScenarioFile.xml";
   }
}
