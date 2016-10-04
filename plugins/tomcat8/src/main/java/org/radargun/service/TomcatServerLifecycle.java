package org.radargun.service;

/**
 * @author Martin Gencur
 */
public class TomcatServerLifecycle extends ProcessLifecycle<TomcatServerService> {

   public TomcatServerLifecycle(final TomcatServerService service) {
      super(service);
   }

   @Override
   public void start() {
      if (service.isTomcatReady()) {
         log.warn("Tomcat is already running");
         return;
      }
      fireBeforeStart();
      try {
         startInternal();
         long timeout = service.startTimeout;
         boolean serverAvailable = false;
         while (timeout > 0 && !serverAvailable) {
            serverAvailable = service.isTomcatReady();
            if (!serverAvailable) {
               try {
                  Thread.sleep(1000);
               } catch (InterruptedException e) {
                  e.printStackTrace();
               }
               timeout -= 1000;
            }
         }
         if (!serverAvailable)
            throw new IllegalStateException("Server did not start within timeout");

         log.info("Tomcat server started by RadarGun!");
      } finally {
         fireAfterStart();
      }
   }

   @Override
   public void stop() {
      if (!isRunning()) {
         log.warn("Tomcat is not running, cannot stop");
         return;
      }
      fireBeforeStop(true);
      stopInternal();
      log.info("Tomcat server stopped by RadarGun");
   }

   @Override
   public void kill() {
      Runnable waiting = killAsyncInternal();
      if (waiting == null) return;
      waiting.run();
      stopReaders();
   }

   @Override
   public void killAsync() {
      Runnable waiting = killAsyncInternal();
      if (waiting == null) return;
      Thread listenerInvoker = new Thread(waiting, "StopListenerInvoker");
      listenerInvoker.setDaemon(true);
      listenerInvoker.start();
      synchronized (this) {
         outputReader = null;
         errorReader = null;
      }
   }

   private synchronized void stopReaders() {
      if (outputReader != null) {
         try {
            outputReader.join(60000);
         } catch (InterruptedException e) {
            log.warn("Interrupted waiting for the reader");
            Thread.currentThread().interrupt();
         }
         outputReader = null;
      }
      if (errorReader != null) {
         try {
            errorReader.join(60000);
         } catch (InterruptedException e) {
            log.warn("Interrupted waiting for the reader");
            Thread.currentThread().interrupt();
         }
         errorReader = null;
      }
   }

}
