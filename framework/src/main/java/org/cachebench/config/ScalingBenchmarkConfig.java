package org.cachebench.config;

import org.cachebench.Stage;
import org.cachebench.DistStage;

import java.util.ArrayList;
import java.util.List;

/**
 * A scaling benchmark is one that executes on an increasing number of slaves. E.g. consdering the {@link
 * org.cachebench.stages.WebSessionBenchmarkStage}, one might want to execute it over multiple clusteres of
 * different sizes: e.g 2,3,4,5..10 etc in order to check how a product scales etc. tailf 2  
 *
 * @author Mircea.Markus@jboss.com
 */
public class ScalingBenchmarkConfig extends FixedSizeBenchmarkConfig {

   private List<Stage> beforeStages = new ArrayList<Stage>();
   private List<Stage> afterStages = new ArrayList<Stage>();

   //mandatory
   private int initSize = -1;
   private int maxSize = -1;


   //optional
   private int increment = 1;

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

   public void setIncrement(int increment) {
      this.increment = increment;
   }

   public void setBeforeStages(List<Stage> beforeStages) {
      this.beforeStages = beforeStages;
   }

   public void setAfterStages(List<Stage> afterStages) {
      this.afterStages = afterStages;
   }

   public void validate() {
      super.validate();
      if (initSize < 2)
         throw new RuntimeException("For scaling benchmarks(" + getName() + ") the initial size must be at least 2");
      if (maxSize < initSize)
         throw new RuntimeException("Config problems for benchmark: " + getName() + " - maxSize must be >= initSize");
      if (increment <= 0) throw new RuntimeException("Increment must be positive!");
   }

   public List<Stage> getAssmbledStages() {
      List<Stage> allStages = new ArrayList<Stage>();
      for (Stage st : beforeStages) {
         if (st instanceof DistStage) {
            ((DistStage) st).setActiveSlavesCount(maxSize);
         }
         allStages.add(st);
      }
      for (int i = initSize; i <= maxSize; i+=increment) {
         for (Stage st : getStages()) {
            if (st instanceof DistStage) {
               st = ((DistStage) st).clone();
               ((DistStage) st).setActiveSlavesCount(i);
            }
            allStages.add(st);
         }
      }
      for (Stage st : afterStages) {
         if (st instanceof DistStage) {
            ((DistStage) st).setActiveSlavesCount(maxSize);
         }
         allStages.add(st);
      }
      return allStages;
   }
}
