package org.radargun.service;

import org.radargun.utils.TimeService;

import java.util.regex.Pattern;

/**
 * @author Matej Cimbora
 */
public class SparkWorkerLifecycle extends AbstractSparkLifecycle {

   private static final Pattern START_PATTERN = Pattern.compile(".*starting org\\.apache\\.spark\\.deploy\\.worker\\.Worker, logging to.*");
   private static final Pattern CONNECTED_TO_MASTER_PATTERN = Pattern.compile(".*Worker: Successfully registered with master.*");

   // Consider worker running only when it's associated with a master
   private boolean connectedToMaster;

   public SparkWorkerLifecycle(SparkWorkerService service) {
      super(service);
   }

   @Override
   protected void startInternal() {
      super.startInternal();
      service.registerAction(CONNECTED_TO_MASTER_PATTERN, m -> {
         setConnectedToMaster();
      });
      long startTime = TimeService.currentTimeMillis();
      synchronized (this) {
         while (!connectedToMaster) {
            try {
               long waitTime = startTime + service.startTimeout - TimeService.currentTimeMillis();
               if (waitTime <= 0) {
                  throw new IllegalStateException("Connection with master wasn't established within timeout");
               }
               wait(waitTime);
            } catch (InterruptedException e) {
               throw new IllegalStateException(e);
            }
         }
      }
   }

   protected synchronized void setConnectedToMaster() {
      connectedToMaster = true;
      notifyAll();
   }

   @Override
   protected Pattern getStartPattern() {
      return START_PATTERN;
   }
}
