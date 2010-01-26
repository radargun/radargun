package org.cachebench.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.DistStage;
import org.cachebench.Stage;
import org.cachebench.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed size benchmark is a benchmark that executes over a fixed number of slaves. This defines the configuration of
 * such a benchmark.
 *
 * @author Mircea.Markus@jboss.com
 * @see org.cachebench.config.ScalingBenchmarkConfig
 */
public class FixedSizeBenchmarkConfig implements Cloneable {

   private static Log log = LogFactory.getLog(FixedSizeBenchmarkConfig.class);

   protected List<Stage> stages = new ArrayList<Stage>();

   protected String productName;
   protected String configName;
   protected int size;

   protected int stIterator = 0;
   private int maxSize = -1;

   public int getMaxSize() {
      return maxSize;
   }

   public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
   }

   public FixedSizeBenchmarkConfig() {
   }

   public void setStages(List<Stage> stages) {
      this.stages = new ArrayList<Stage>(stages);
   }

   public void addStage(Stage stage) {
      stages.add(stage);
   }

   public List<Stage> getStages() {
      return new ArrayList<Stage>(stages);
   }

   public String getProductName() {
      return productName;
   }

   public void setProductName(String productName) {
      assertNo_(productName);
      this.productName = productName;
   }

   private void assertNo_(String name) {
      if (name.indexOf("_") >= 0) {
         throw new RuntimeException("'_' not allowed in productName (reporting relies on that)");
      }
   }

   public String getConfigName() {
      return configName;
   }

   public void setConfigName(String configName) {
      configName = Utils.fileName2Config(configName);
      assertNo_(configName);
      this.configName = configName;
   }

   public void validate() {
      if (productName == null) throw new RuntimeException("Name must be set!");
   }

   public void setSize(int size) {
      this.size = size;
   }

   @Override
   public FixedSizeBenchmarkConfig clone() {
      FixedSizeBenchmarkConfig clone;
      try {
         clone = (FixedSizeBenchmarkConfig) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new RuntimeException("Impossible!!!");
      }
      clone.stages = cloneStages(this.stages);
      return clone;
   }

   public boolean hasNextStage() {
      return stIterator < stages.size();
   }

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

   public void errorOnCurentBenchmark() {
      log.trace("Issues in curent benchmark, skipping remaining stages");
      stIterator = stages.size();
   }

   protected List<Stage> cloneStages(List<Stage> stages) {
      List<Stage> clone = new ArrayList<Stage>();
      for (Stage st : stages) {
         clone.add(st.clone());
      }
      return clone;
   }
}
