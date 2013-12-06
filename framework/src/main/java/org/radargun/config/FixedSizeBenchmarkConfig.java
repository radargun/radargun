package org.radargun.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.DistStage;
import org.radargun.Stage;

/**
 * A fixed size benchmark is a benchmark that executes over a fixed number of slaves. This defines the configuration of
 * such a benchmark.
 *
 * @author Mircea.Markus@jboss.com
 * @see org.radargun.config.ScalingBenchmarkConfig
 */
public class FixedSizeBenchmarkConfig extends AbstractBenchmarkConfig {

   private static Log log = LogFactory.getLog(FixedSizeBenchmarkConfig.class);

   private List<Stage> stages = new ArrayList<Stage>();
   private int size;
   private int maxSize = -1;
   private int stIterator = 0;

   public int getSize() {
      return size;
   }

   public void setSize(int size) {
      this.size = size;
   }

   public int getMaxSize() {
      return maxSize;
   }

   public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
   }

   public FixedSizeBenchmarkConfig() {
   }

   public void addStage(Stage stage) {
      stages.add(stage);
   }

   public List<Stage> getStages() {
      return new ArrayList<Stage>(stages);
   }

   @Override
   public void validate() {
      if (productName == null) throw new RuntimeException("Name must be set!");
   }

   @Override
   public FixedSizeBenchmarkConfig clone() {
      FixedSizeBenchmarkConfig clone;
      try {
         clone = (FixedSizeBenchmarkConfig) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new RuntimeException("Impossible!!!");
      }
      clone.stages = new ArrayList<Stage>();
      for (Stage st : stages) {
         clone.stages.add(st.clone());
      }
      return clone;
   }

   @Override
   public boolean hasNextStage() {
      return stIterator < stages.size();
   }

   @Override
   public Stage nextStage() {
      Stage stage = stages.get(stIterator);
      stIterator++;
      if (stage instanceof DistStage) {
         DistStage distStage = (DistStage) stage;
         if (!distStage.isRunOnAllSlaves()) {
            distStage.setActiveSlavesCount(size);
         } else {
            if (maxSize <= 0) throw new IllegalStateException("Make sure you set the maxSize first!");
            distStage.setActiveSlavesCount(maxSize);
         }
      }
      return stage;
   }

   public void errorOnCurrentBenchmark() {
      log.trace("Issues in current benchmark, skipping remaining stages");
      stIterator = stages.size();
   }
}
