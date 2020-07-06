package org.radargun.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comntains main's configuration elements.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class MainConfig {
   private byte[] mainConfigBytes;
   private byte[] scenarioBytes;
   private final int port;
   private final String host;
   private List<Cluster> clusters = new ArrayList<Cluster>();
   private List<Configuration> configurations = new ArrayList<Configuration>();
   private Map<String, Configuration.SetupBase> templates = new HashMap<>();
   private Scenario scenario;
   private List<ReporterConfiguration> reporters = new ArrayList<ReporterConfiguration>();

   public MainConfig(int port, String host) {
      this.port = port;
      this.host = host;
   }

   public void addCluster(Cluster cluster) {
      clusters.add(cluster);
   }

   public void addConfig(Configuration config) {
      for (Configuration c : configurations) {
         if (c.name.equals(config.name))
            throw new IllegalArgumentException("Cannot have two configurations named " + config.name);
      }
      configurations.add(config);
   }

   public void addTemplate(String name, String base, Map<String, Definition> propertyDefinitions, Map<String, Definition> vmArgs, Map<String, Definition> envs) {
      if (templates.put(name, new Configuration.SetupBase(base, vmArgs, propertyDefinitions, envs)) != null) {
         throw new IllegalArgumentException("The configuration already contains template '" + name + "'");
      }
   }

   public void setScenario(Scenario scenario) {
      this.scenario = scenario;
   }

   public void addReporter(ReporterConfiguration reporter) {
      this.reporters.add(reporter);
   }

   public int getPort() {
      return port;
   }

   public String getHost() {
      return host;
   }

   public List<Cluster> getClusters() {
      return Collections.unmodifiableList(clusters);
   }

   public int getMaxClusterSize() {
      int max = 0;
      for (Cluster c : clusters) {
         max = Math.max(max, c.getSize());
      }
      return max;
   }

   public List<Configuration> getConfigurations() {
      return Collections.unmodifiableList(configurations);
   }

   public Scenario getScenario() {
      return scenario;
   }

   public List<ReporterConfiguration> getReporters() {
      return Collections.unmodifiableList(reporters);
   }

   // this method is used from tests.sh
   public Set<String> getPlugins() {
      Set<String> plugins = new HashSet<>();
      for (Configuration configuration : configurations) {
         for (Configuration.Setup setup : configuration.getSetups()) {
            plugins.add(setup.plugin);
         }
      }
      return plugins;
   }

   public void setMainConfigBytes(byte[] configBytes) {
      this.mainConfigBytes = configBytes;
   }

   public byte[] getMainConfigBytes() {
      return mainConfigBytes;
   }

   public void setScenarioBytes(byte[] scenarioBytes) {
      this.scenarioBytes = scenarioBytes;
   }

   public byte[] getScenarioBytes() {
      return scenarioBytes;
   }

   public void applyTemplates() {
      for (int i = 0; i < configurations.size(); i++) {
         Configuration configuration = configurations.get(i);
         Configuration newConfiguration = new Configuration(configuration.name);
         for (Configuration.Setup setup : configuration.getSetups()) {
            Configuration.Setup newSetup = setup.applyTemplates(templates);
            newConfiguration.addSetup(newSetup);
         }
         configurations.set(i, newConfiguration);
      }
   }
}
