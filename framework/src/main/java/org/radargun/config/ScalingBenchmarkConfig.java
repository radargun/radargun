package org.radargun.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.Stage;

/**
 * A scaling benchmark is one that executes on an increasing number of slaves. E.g. considering the {@link
 * org.radargun.stages.StressTestStage}, one might want to execute it over multiple clusters of
 * different sizes: e.g 2,3,4,5..10 etc in order to check how a product scales etc.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ScalingBenchmarkConfig extends FixedSizeBenchmarkConfig {

   public enum IncrementMethod {
      ADD,
      MULTIPLY
   }

   // For Apache/commons/logging Log doesn't need to be static.
   protected Log log = LogFactory.getLog(ScalingBenchmarkConfig.class);

   private boolean initialized = false;

   List<FixedSizeBenchmarkConfig> fixedBenchmarks = new ArrayList<FixedSizeBenchmarkConfig>();
   private int fixedBenchmarkIt = 0;

   //mandatory
   private int initSize = -1;


   //optional
   private int increment = 1;
   private IncrementMethod incrementMethod = IncrementMethod.ADD;

   public int getInitSize() {
      return initSize;
   }

   public void setInitSize(int initSize) {
      this.initSize = initSize;
   }

   public int getIncrement() {
      return increment;
   }

   public void setIncrement(int increment, IncrementMethod method) {
      this.increment = increment;
      this.incrementMethod = method;
   }

   public void validate() {
      super.validate();
      if (initSize < 2)
         throw new RuntimeException("For scaling benchmarks(" + getProductName() + ") the initial size must be at least 2");
      if (getMaxSize() < initSize)
         throw new RuntimeException("Config problems for benchmark: " + getProductName() + " - maxSize must be >= initSize");
      if (increment <= 0) throw new RuntimeException("Increment must be positive!");
   }

   @Override
   public boolean hasNextStage() {
      initialize();
      log.trace("fixedBenchmarkIt="+fixedBenchmarkIt);
      if (fixedBenchmarkIt < fixedBenchmarks.size() - 1) return true;
      return currentFixedBenchmark().hasNextStage();
   }

   private void initialize() {
      if (!initialized) {
         log.info("Initializing.  Starting with " + initSize + " nodes, up to "+ getMaxSize() + " nodes, incrementing "
               + (incrementMethod == IncrementMethod.ADD ? "by " : "times ") + increment);
         for (int size = initSize; size <= getMaxSize(); ) {
            log.info("Initializing configuration with " + size + " nodes");
            FixedSizeBenchmarkConfig conf = new FixedSizeBenchmarkConfig();
            conf.setMaxSize(getMaxSize());
            conf.stages = cloneStages(this.stages);
            conf.setSize(size);
            conf.setConfigName(super.configName);
            conf.setProductName(super.productName);
            fixedBenchmarks.add(conf);
            if (increment == 0) break;
            switch (incrementMethod) {
               case ADD:
                  size += increment;
                  break;
               case MULTIPLY:
                  size *= increment;
                  break;
            }
         }
         initialized = true;
         log.info("Number of cluster topologies on which benchmark will be executed is " + fixedBenchmarks.size());
      }
   }

   @Override
   public Stage nextStage() {
      initialize();
      if (!currentFixedBenchmark().hasNextStage()) {
         fixedBenchmarkIt++;
      }
      return currentFixedBenchmark().nextStage();
   }

   private FixedSizeBenchmarkConfig currentFixedBenchmark() {
      return fixedBenchmarks.get(fixedBenchmarkIt);
   }

   public void errorOnCurrentBenchmark() {
      currentFixedBenchmark().errorOnCurrentBenchmark();
   }

   @Override
   public ScalingBenchmarkConfig clone() {
      ScalingBenchmarkConfig clone = (ScalingBenchmarkConfig) super.clone();
      clone.fixedBenchmarks = new ArrayList<FixedSizeBenchmarkConfig>();
      for (FixedSizeBenchmarkConfig fbc : fixedBenchmarks) {
         clone.fixedBenchmarks.add(fbc.clone());
      }
      return clone;
   }

}
