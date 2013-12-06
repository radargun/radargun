package org.radargun.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Comntains master's configuration elements.
 *
 * @author Mircea.Markus@jboss.com
 */
public class MasterConfig {
   private int port;
   private String host;
   private int slavesCount;

   List<AbstractBenchmarkConfig> benchmarks = new ArrayList<AbstractBenchmarkConfig>();

   public MasterConfig(int port, String host, int slavesCount) {
      this.port = port;
      this.host = host;
      this.slavesCount = slavesCount;
   }

   public int getPort() {
      return port;
   }

   public String getHost() {
      return host;
   }

   public int getSlaveCount() {
      return slavesCount;
   }

   public List<AbstractBenchmarkConfig> getBenchmarks() {
      return benchmarks;
   }

   public void addBenchmark(AbstractBenchmarkConfig config) {
      benchmarks.add(config);
   }

   public void validate() {
      Set<String> allBenchmarkNames = new HashSet<String>();
      for (AbstractBenchmarkConfig f: benchmarks) {
         if (!allBenchmarkNames.add(f.getProductName())) {
            throw new RuntimeException("There are two benchmarks having same name:" + f.getProductName() + ". Benchmark name should be unique!");
         }
      }
   }
}
