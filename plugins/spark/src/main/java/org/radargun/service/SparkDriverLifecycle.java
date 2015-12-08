package org.radargun.service;

import org.radargun.traits.Lifecycle;

/**
 * @author Matej Cimbora
 */
public class SparkDriverLifecycle implements Lifecycle {

   private SparkDriverService sparkDriverService;

   public SparkDriverLifecycle(SparkDriverService sparkDriverService) {
      this.sparkDriverService = sparkDriverService;
   }

   @Override
   public void start() {
      sparkDriverService.startSparkContext();
   }

   @Override
   public void stop() {
      sparkDriverService.sparkContext.stop();
      sparkDriverService.sparkContext = null;
   }

   @Override
   public boolean isRunning() {
      return sparkDriverService.sparkContext != null;
   }
}
