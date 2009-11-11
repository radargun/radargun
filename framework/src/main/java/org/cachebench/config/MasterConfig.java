package org.cachebench.config;

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

   List<FixedSizeBenchmarkConfig> benchmarks = new ArrayList<FixedSizeBenchmarkConfig>();

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

   public List<FixedSizeBenchmarkConfig> getBenchmarks() {
      return benchmarks;
   }

   public void addBenchmark(FixedSizeBenchmarkConfig config) {
      benchmarks.add(config);
   }

   public void validate() {
      Set<String> allBenchmarkNames = new HashSet<String>();
      for (FixedSizeBenchmarkConfig f: benchmarks) {
         if (!allBenchmarkNames.add(f.getName())) {
            throw new RuntimeException("There are two benchmarks having same name:" + f.getName() + ". Benchmark name should be unique!");
         }
      }
   }
}
