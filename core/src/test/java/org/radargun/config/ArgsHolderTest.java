package org.radargun.config;

import java.util.List;
import java.util.Map;

import org.radargun.utils.ArgsHolder;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
@Test
public class ArgsHolderTest {

   public void testParseArgs() {
      String[] mainArgs = {"--config", "/foo/configFile.xml", "--add-reporter=/foo/reporterDir", "--add-reporter=/bar/reporterDir"};
      String[] workerArgs = {"--main", "127.0.0.1:2103", "--workerIndex", "1", "--add-plugin=/foo/plugin1", "--add-config=plugin1:/foo/config.xml",
         "--add-config=plugin1:/foo/jgroups.xml", "--add-plugin=/bar/plugin2", "--add-config=plugin2:/bar/config.xml"};
      ArgsHolder.init(mainArgs, ArgsHolder.ArgType.LAUNCH_MAIN);
      ArgsHolder.init(workerArgs, ArgsHolder.ArgType.WORKER);

      assertEquals("127.0.0.1", ArgsHolder.getMainHost());
      assertEquals(2103, ArgsHolder.getMainPort());
      assertEquals(1, ArgsHolder.getWorkerIndex());
      assertEquals("/foo/configFile.xml", ArgsHolder.getConfigFile());

      List<String> reporterPaths = ArgsHolder.getReporterPaths();
      assertTrue(reporterPaths.contains("/bar/reporterDir"));
      assertTrue(reporterPaths.contains("/foo/reporterDir"));

      Map<String, ArgsHolder.PluginParam> pluginParams = ArgsHolder.getPluginParams();
      ArgsHolder.PluginParam plugin1 = pluginParams.get("plugin1");
      assertNotNull(plugin1);
      assertEquals("/foo/plugin1", plugin1.getPath());
      List<String> configFiles1 = plugin1.getConfigFiles();
      assertTrue(configFiles1.contains("/foo/config.xml"));
      assertTrue(configFiles1.contains("/foo/jgroups.xml"));

      ArgsHolder.PluginParam plugin2 = pluginParams.get("plugin2");
      assertNotNull(plugin2);
      assertEquals("/bar/plugin2", plugin2.getPath());
      List<String> configFiles2 = plugin2.getConfigFiles();
      assertTrue(configFiles2.contains("/bar/config.xml"));

      String[] deprecatedMainArgs = {"-config", "/deprecated/configFile.xml"};
      String[] deprecatedWorkerArgs = {"-main", "127.0.0.2:2101", "-workerIndex", "2"};
      ArgsHolder.init(deprecatedMainArgs, ArgsHolder.ArgType.LAUNCH_MAIN);
      ArgsHolder.init(deprecatedWorkerArgs, ArgsHolder.ArgType.WORKER);

      assertEquals("127.0.0.2", ArgsHolder.getMainHost());
      assertEquals(2101, ArgsHolder.getMainPort());
      assertEquals(2, ArgsHolder.getWorkerIndex());
      assertEquals("/deprecated/configFile.xml", ArgsHolder.getConfigFile());
   }
}
