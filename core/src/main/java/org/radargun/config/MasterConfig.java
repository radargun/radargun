package org.radargun.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Comntains master's configuration elements.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class MasterConfig {
   private byte[] masterConfigBytes;
   private byte[] scenarioBytes;
   private final int port;
   private final String host;
   private List<Cluster> clusters = new ArrayList<Cluster>();
   private List<Configuration> configurations = new ArrayList<Configuration>();
   private Scenario scenario;
   private List<ReporterConfiguration> reporters = new ArrayList<ReporterConfiguration>();

   public MasterConfig(int port, String host) {
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

   public void setMasterConfigBytes(byte[] configBytes) {
      this.masterConfigBytes = configBytes;
   }

   public byte[] getMasterConfigBytes() {
      return masterConfigBytes;
   }

   public void setScenarioBytes(byte[] scenarioBytes) {
      this.scenarioBytes = scenarioBytes;
   }

   public byte[] getScenarioBytes() {
      return scenarioBytes;
   }
}
