package org.cachebench.config;

import org.cachebench.Stage;
import org.cachebench.DistStage;

import java.util.List;
import java.util.ArrayList;

/**
 * A fixed size benchmark is a benchmark that executes over a fixed number of slaves. This defines the configuration of
 * such a benchmark.
 *
 * @see org.cachebench.config.ScalingBenchmarkConfig
 * @author Mircea.Markus@jboss.com
 */
public class FixedSizeBenchmarkConfig {

   private List<Stage> stages = new ArrayList<Stage>();

   private String name;
   private int size;


   public void setStages(List<Stage> stages) {
      this.stages = stages;
   }

   public List<Stage> getStages() {
      return new ArrayList<Stage>(stages);
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void validate() {
      if (name == null) throw new RuntimeException("Name must be set!");
   }

   public List<Stage> getAssmbledStages() {
      for (Stage st: stages) {
         if (st instanceof DistStage) {
            ((DistStage)st).setActiveSlavesCount(size);
         }
      }
      return stages;
   }

   public void setSize(int size) {
      this.size = size;
   }
}
