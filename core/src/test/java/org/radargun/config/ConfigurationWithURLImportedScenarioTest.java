package org.radargun.config;

/**
 * Variation of AbstractConfigurationTest when scenario is imported from URL
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class ConfigurationWithURLImportedScenarioTest extends AbstractConfigurationTest {

   @Override
   protected String getBenchmark() {
      return "benchmark-importedScenarioFromURL.xml";
   }
}
