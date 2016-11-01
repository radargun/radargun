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
