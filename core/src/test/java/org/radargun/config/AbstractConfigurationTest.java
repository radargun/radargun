package org.radargun.config;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.testng.PowerMockTestCase;
import org.radargun.LaunchMaster;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.ReporterHelper;
import org.radargun.stages.control.RepeatBeginStage;
import org.radargun.stages.control.RepeatContinueStage;
import org.radargun.stages.control.RepeatEndStage;
import org.radargun.stages.lifecycle.ServiceStartStage;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Tests parsing of benchmark configuration file
 *
 * @author Roman Macor &lt;rmacor@redhat.com&gt;
 */
@Test
@PowerMockIgnore({"javax.management.*"})
@PrepareForTest(StageHelper.class)
@SuppressStaticInitializationFor({"org.radargun.config.StageHelper", "org.radargun.reporting.ReporterHelper"})
public abstract class AbstractConfigurationTest extends PowerMockTestCase {
   protected static Log log = LogFactory.getLog(AbstractConfigurationTest.class);
   protected MasterConfig masterConfig;
   protected List<String> resources = new ArrayList<>();

   protected abstract String getBenchmark();

   /**
    * Copies resources to the testing directory (such as benchmark file or scenario file)
    * Parses benchmark file and mocks classes that are irrelevant to the tests
    *
    * @throws Exception
    */
   @BeforeClass
   public void setUpClass() throws Exception {

      PowerMockito.field(ReporterHelper.class, "reporters").set(ReporterHelper.class, mock(HashMap.class));

      DomConfigParser configParser = Mockito.mock(DomConfigParser.class, CALLS_REAL_METHODS);
      PowerMockito.mockStatic(StageHelper.class);
      PowerMockito.when(StageHelper.getStageClassByDashedName(anyString())).thenAnswer(new Answer<Class<ServiceStartStage>>() {
         @Override
         public Class<ServiceStartStage> answer(InvocationOnMock invocationOnMock) throws Throwable {
            return ServiceStartStage.class;
         }
      });
      resources.add(getBenchmark());
      copyResources();
      String[] masterArgs = {"--config", getBenchmark()};
      String config = LaunchMaster.getConfigOrExit(masterArgs);

      log.info("Configuration file is: " + config);
      log.info("Current directory is " + System.getProperty("user.dir"));

      masterConfig = configParser.parseConfig(config);
   }

   /**
    * Removes resource files that were copied to the testing directory in setUpClass method
    */
   @AfterClass
   public void cleanUp() {
      for (String file : resources) {
         File benchmarkFile = new File(System.getProperty("user.dir") + File.separator + file);
         benchmarkFile.delete();
      }
   }

   /**
    * Tests correct parsing of configuration
    */
   public void testConfiguration() {
      List<Configuration> configurations = masterConfig.getConfigurations();

      assertEquals(configurations.size(), 2);

      Configuration infinispan52 = configurations.get(0);
      assertEquals(infinispan52.name, "Infinispan 5.2 - distributed");

      Configuration infinispan60 = configurations.get(1);
      assertEquals(infinispan60.name, "Infinispan 6.0 - distributed");

      assertConfiguration(infinispan52, "infinispan52");
      assertConfiguration(infinispan60, "infinispan60");
   }

   /**
    * Helper method to tests correct parsing of configuration
    *
    * @param configuration      benchmark configuration
    * @param expectedPluginName plugin name
    */
   private void assertConfiguration(Configuration configuration, String expectedPluginName) {
      assertEquals(configuration.getSetups().size(), 1);
      Configuration.Setup setup = configuration.getSetups().get(0);

      assertEquals(setup.plugin.toString(), expectedPluginName);
      assertEquals(setup.service.toString(), "embedded");

      assertEquals(setup.getProperties().size(), 1);
      assertEquals(setup.getVmArgs().size(), 1);

      assertTrue(setup.getProperties().containsKey("file"));
      assertEquals(setup.getProperties().get("file").toString(), "dist-sync.xml");

      assertTrue(setup.getVmArgs().containsKey("memory"));
      assertEquals(setup.getVmArgs().get("memory").toString(), "max=4G");
   }

   /**
    * Tests correct parsing of configuration
    */
   public void testHost() {
      String host = masterConfig.getHost();
      int port = masterConfig.getPort();
      assertEquals(host, "127.0.0.1");
      assertEquals(port, 2103);
   }

   /**
    * Tests correct parsing of plugins configuration
    */
   public void testPlugins() {
      Set<String> plugins = masterConfig.getPlugins();

      assertEquals(plugins.size(), 2);

      assertTrue(plugins.contains("infinispan52"));
      assertTrue(plugins.contains("infinispan60"));
   }

   /**
    * Tests correct parsing of clusters configuration
    */
   public void testClusters() {
      List<Cluster> clusters = masterConfig.getClusters();

      assertEquals(clusters.size(), 2);

      assertEquals(clusters.get(0).toString(), "Cluster[default=2]");
      assertEquals(clusters.get(1).toString(), "Cluster[default=3]");

      assertEquals(masterConfig.getMaxClusterSize(), 3);
   }

   /**
    * Tests correct parsing of scenario configuration
    */
   public void testScenario() {
      Scenario scenario = masterConfig.getScenario();
      assertEquals(scenario.getStageCount(), 14);
      List<Scenario.StageDescription> stages = scenario.getStages();

      Map<String, Definition> basicOperationTestProperties = stages.get(4).properties;
      assertEquals(basicOperationTestProperties.size(), 4);

      assertTrue(basicOperationTestProperties.containsKey("test-name"));
      assertTrue(basicOperationTestProperties.containsKey("num-threads-per-node"));
      assertTrue(basicOperationTestProperties.containsKey("num-requests"));
      assertTrue(basicOperationTestProperties.containsKey("key-selector"));

      for (Scenario.StageDescription stage : stages) {
         Map<String, Definition> properties = stage.properties;
         if (stage.stageClass.equals(RepeatBeginStage.class) || stage.stageClass.equals(RepeatContinueStage.class) ||
                 stage.stageClass.equals(RepeatEndStage.class))
            assertRepeatProperties(properties);
      }
   }

   /**
    * Tests correct parsing of repeat properties
    *
    * @param properties repeat stage properties
    */
   private void assertRepeatProperties(Map<String, Definition> properties) {
      assertEquals(properties.size(), 3);

      assertTrue(properties.containsKey("from"));
      assertTrue(properties.containsKey("to"));
      assertTrue(properties.containsKey("inc"));

      assertFalse(properties.containsKey("madeUp"));

      assertEquals(properties.get("from").toString(), "10");
      assertEquals(properties.get("to").toString(), "30");
      assertEquals(properties.get("inc").toString(), "10");
   }

   /**
    * Tests correct parsing of report configuration
    */
   public void testReport() {
      List<ReporterConfiguration> reporters = masterConfig.getReporters();

      assertEquals(reporters.size(), 3);
      assertEquals(reporters.get(0).type, "csv");
      assertEquals(reporters.get(1).type, "html");
      assertEquals(reporters.get(2).type, "serialized");
   }

   /**
    * Copies resources to the current directory
    *
    * @throws IOException if file can not be copied
    */
   protected void copyResources() {
      ClassLoader classLoader = getClass().getClassLoader();
      for (String file : resources) {
         try (InputStream source = classLoader.getResourceAsStream(file)) {
            File destination = new File(System.getProperty("user.dir") + File.separator + file);
            Files.copy(source, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            log.error("Exception while copying resources", e);
         }
      }
   }
}
