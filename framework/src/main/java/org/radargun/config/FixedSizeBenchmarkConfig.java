package org.radargun.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.radargun.DistStage;
import org.radargun.Stage;
import org.radargun.utils.TypedProperties;
import org.radargun.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed size benchmark is a benchmark that executes over a fixed number of slaves. This defines the configuration of
 * such a benchmark.
 *
 * @author Mircea.Markus@jboss.com
 * @see org.radargun.config.ScalingBenchmarkConfig
 */
public class FixedSizeBenchmarkConfig implements Cloneable {

   private static Log log = LogFactory.getLog(FixedSizeBenchmarkConfig.class);

   protected List<Stage> stages = new ArrayList<Stage>();

   protected String productName;
   protected String configName;
   protected int size;

   private TypedProperties configAttributes;


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

   public void setConfigAttributes(TypedProperties typedProperties) {
      this.configAttributes = typedProperties;
   }

   public TypedProperties getConfigAttributes() {
      return configAttributes;
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

   public void errorOnCurrentBenchmark() {
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
