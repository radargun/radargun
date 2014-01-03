package org.radargun.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.Stage;

/**
 * A scaling benchmark is one that executes on an increasing number of slaves. E.g. considering the {@link
 * org.radargun.stages.StressTestStage}, one might want to execute it over multiple clusters of
 * different sizes: e.g 2,3,4,5..10 etc in order to check how a product scales etc.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ScalingBenchmarkConfig extends AbstractBenchmarkConfig {

   public enum IncrementMethod {
      ADD,
      MULTIPLY
   }

   // For Apache/commons/logging Log doesn't need to be static.
   protected Log log = LogFactory.getLog(ScalingBenchmarkConfig.class);

   List<FixedSizeBenchmarkConfig> fixedBenchmarks = new ArrayList<FixedSizeBenchmarkConfig>();
   private int fixedBenchmarkIt = 0;

   //mandatory
   private int initSize = -1;
   private int maxSize = -1;


   //optional
   private int increment = 1;
   private IncrementMethod incrementMethod = IncrementMethod.ADD;

   public int getInitSize() {
      return initSize;
   }

   public void setInitSize(int initSize) {
      this.initSize = initSize;
   }

   public int getMaxSize() {
      return maxSize;
   }

   public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
   }

   public int getIncrement() {
      return increment;
   }

   public void setIncrement(int increment, IncrementMethod method) {
      this.increment = increment;
      this.incrementMethod = method;
   }

   @Override
   public void setProductName(String productName) {
      for (FixedSizeBenchmarkConfig benchmark : fixedBenchmarks) {
         benchmark.setProductName(productName);
      }
      super.setProductName(productName);
   }

   @Override
   public void setConfigName(String configName) {
      for (FixedSizeBenchmarkConfig benchmark : fixedBenchmarks) {
         benchmark.setConfigName(configName);
      }
      super.setConfigName(configName);
   }

   public void validate() {
      if (initSize < 1)
         throw new RuntimeException("For scaling benchmarks(" + getProductName() + ") the initial size must be at least 1");
      if (getMaxSize() < initSize)
         throw new RuntimeException("Config problems for benchmark: " + getProductName() + " - maxSize must be >= initSize");
      if (increment <= 0) throw new RuntimeException("Increment must be positive!");
   }

   @Override
   public boolean hasNextStage() {
      log.trace("fixedBenchmarkIt="+fixedBenchmarkIt);
      if (fixedBenchmarkIt < fixedBenchmarks.size() - 1) return true;
      return currentFixedBenchmark().hasNextStage();
   }

   public Collection<FixedSizeBenchmarkConfig> initBenchmarks() {
      log.info("Initializing.  Starting with " + initSize + " nodes, up to "+ getMaxSize() + " nodes, incrementing "
            + (incrementMethod == IncrementMethod.ADD ? "by " : "times ") + increment);
      for (int size = initSize; size <= getMaxSize(); ) {
         log.info("Initializing configuration with " + size + " nodes");
         FixedSizeBenchmarkConfig conf = new FixedSizeBenchmarkConfig();
         conf.setMaxSize(getMaxSize());
         conf.setSize(size);
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
      log.info("Number of cluster topologies on which benchmark will be executed is " + fixedBenchmarks.size());
      return getBenchmarks();
   }

   public Collection<FixedSizeBenchmarkConfig> getBenchmarks() {
      return Collections.unmodifiableCollection(fixedBenchmarks);
   }

   @Override
   public Stage nextStage() {
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
      ScalingBenchmarkConfig clone = null;
      try {
         clone = (ScalingBenchmarkConfig) super.clone();
      } catch (CloneNotSupportedException e) {
         throw new RuntimeException(e);
      }
      clone.fixedBenchmarks = new ArrayList<FixedSizeBenchmarkConfig>();
      for (FixedSizeBenchmarkConfig fbc : fixedBenchmarks) {
         clone.fixedBenchmarks.add(fbc.clone());
      }
      return clone;
   }

}
