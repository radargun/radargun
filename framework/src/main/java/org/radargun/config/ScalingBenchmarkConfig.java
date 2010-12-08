package org.radargun.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import org.radargun.Stage;

import java.util.List;
import java.util.ArrayList;

/**
 * A scaling benchmark is one that executes on an increasing number of slaves. E.g. consdering the {@link
 * org.radargun.stages.WebSessionBenchmarkStage}, one might want to execute it over multiple clusteres of
 * different sizes: e.g 2,3,4,5..10 etc in order to check how a product scales etc.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ScalingBenchmarkConfig extends FixedSizeBenchmarkConfig {

   // For Apache/commons/logging Log doesn't need to be static.
   protected Log log = LogFactory.getLog(ScalingBenchmarkConfig.class);

   private boolean initialized = false;

   List<FixedSizeBenchmarkConfig> fixedBenchmarks = new ArrayList<FixedSizeBenchmarkConfig>();
   private int fixedBenchmarkIt = 0;

   //mandatory
   private int initSize = -1;


   //optional
   private int increment = 1;

   public int getInitSize() {
      return initSize;
   }

   public void setInitSize(int initSize) {
      this.initSize = initSize;
   }

   public int getIncrement() {
      return increment;
   }

   public void setIncrement(int increment) {
      this.increment = increment;
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
      log.info("fixedBenchmarkIt="+fixedBenchmarkIt);
      log.info("fixedBenchmarks size=" + fixedBenchmarks.size());
      if (fixedBenchmarkIt < fixedBenchmarks.size() - 1) return true;
      return currentFixedBenchmark().hasNextStage();
   }

   private void initialize() {
      if (!initialized) {
      log.info("Initializing: " + initSize + ","+ getMaxSize() + "," + increment);
         for (int i = initSize; i <= getMaxSize(); i+=increment) {
            log.info("Initializing config " + i);
            FixedSizeBenchmarkConfig conf = new FixedSizeBenchmarkConfig();
            conf.setMaxSize(getMaxSize());
            conf.stages = cloneStages(this.stages);
            conf.setSize(i);
            conf.setConfigName(super.configName);
            conf.setProductName(super.productName);
            fixedBenchmarks.add(conf);
         }
         initialized = true;
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

   public void errorOnCurentBenchmark() {
      currentFixedBenchmark().errorOnCurentBenchmark();
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
