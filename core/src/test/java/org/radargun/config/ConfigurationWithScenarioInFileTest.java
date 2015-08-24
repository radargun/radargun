package org.radargun.config;

/**
 * Variation of AbstractConfigurationTest when scenario is not imported
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
public class ConfigurationWithScenarioInFileTest extends AbstractConfigurationTest {

   @Override
   protected String getBenchmark() {
      return "benchmark-test.xml";
   }
}
