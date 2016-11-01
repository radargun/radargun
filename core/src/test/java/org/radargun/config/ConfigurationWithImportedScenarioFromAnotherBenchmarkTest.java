package org.radargun.config;

import org.testng.annotations.Test;

/**
 * Variation of AbstractConfigurationTest when scenario is imported from another benchmark file
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
@Test
public class ConfigurationWithImportedScenarioFromAnotherBenchmarkTest extends AbstractConfigurationTest {
   public ConfigurationWithImportedScenarioFromAnotherBenchmarkTest() {
      resources.add("benchmark-test.xml");
   }

   @Override
   protected String getBenchmark() {
      return "benchmark-importedScenarioFromAnotherBenchmark.xml";
   }
   
   @Override //TODO remove once #431 is done
   public void testClusters() {
      super.testClusters();
   }
   
   @Override //TODO remove once #431 is done
   public void testConfiguration() {
      super.testConfiguration();
   }
   
   @Override //TODO remove once #431 is done
   public void testHost() {
      super.testHost();
   }
   
   @Override //TODO remove once #431 is done
   public void testPlugins() {
      super.testPlugins();
   }
   
   @Override //TODO remove once #431 is done
   public void testReport() {
      super.testReport();
   }
   
   @Override //TODO remove once #431 is done
   public void testScenario() {
      super.testScenario();
   }
}
