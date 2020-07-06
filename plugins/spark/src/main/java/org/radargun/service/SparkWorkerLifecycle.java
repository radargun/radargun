package org.radargun.service;

import java.util.regex.Pattern;

import org.radargun.utils.TimeService;

/**
 * @author Matej Cimbora
 */
public class SparkWorkerLifecycle extends AbstractSparkLifecycle {

   private static final Pattern START_PATTERN = Pattern.compile(".*starting org\\.apache\\.spark\\.deploy\\.worker\\.Worker, logging to.*");
   private static final Pattern CONNECTED_TO_MAIN_PATTERN = Pattern.compile(".*Worker: Successfully registered with main.*");

   // Consider worker running only when it's associated with a main
   private boolean connectedToMain;

   public SparkWorkerLifecycle(SparkWorkerService service) {
      super(service);
   }

   @Override
   protected void startInternal() {
      service.registerAction(CONNECTED_TO_MAIN_PATTERN, m -> {
         setConnectedToMain();
      });
      super.startInternal();
      long startTime = TimeService.currentTimeMillis();
      synchronized (this) {
         while (!connectedToMain) {
            try {
               long waitTime = startTime + service.startTimeout - TimeService.currentTimeMillis();
               if (waitTime <= 0) {
                  throw new IllegalStateException("Connection with main wasn't established within timeout");
               }
               wait(waitTime);
            } catch (InterruptedException e) {
               throw new IllegalStateException(e);
            }
         }
      }
   }

   protected synchronized void setConnectedToMain() {
      connectedToMain = true;
      notifyAll();
   }

   @Override
   protected Pattern getStartPattern() {
      return START_PATTERN;
   }
}
