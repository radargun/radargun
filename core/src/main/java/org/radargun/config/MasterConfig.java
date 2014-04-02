package org.radargun.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comntains master's configuration elements.
 *
 * @author Mircea.Markus@jboss.com
 */
public class MasterConfig {
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

   public boolean isLocal() {
      return clusters.size() == 0;
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
}
