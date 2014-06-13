package org.radargun.service;

import java.util.regex.Pattern;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanServerLifecycle extends ProcessLifecycle {
   private boolean serverStarted;
   private boolean serverStopped;
   private InfinispanServerService service;
   private final static Pattern START_OK = Pattern.compile(".*\\[org\\.jboss\\.as\\].*started in.*");
   private final static Pattern START_ERROR = Pattern.compile(".*\\[org\\.jboss\\.as\\].*started \\(with errors\\) in.*");
   private final static Pattern STOPPED = Pattern.compile(".*\\[org\\.jboss\\.as\\].*stopped in.*");

   public InfinispanServerLifecycle(final InfinispanServerService service) {
      super(service);
      this.service = service;
   }

   @Override
   public synchronized void start() {
      service.registerAction(START_OK, new Runnable() {
         @Override
         public void run() {
            setServerStarted();
            service.unregisterAction(START_OK);
            service.unregisterAction(START_ERROR);
         }
      });
      service.registerAction(START_ERROR, new Runnable() {
         @Override
         public void run() {
            log.warn("Server started with errors");
            setServerStarted();
            service.unregisterAction(START_OK);
            service.unregisterAction(START_ERROR);
         }
      });
      service.registerAction(STOPPED, new Runnable() {
         @Override
         public void run() {
            log.error("Server stopped before it started!");
            setServerStarted();
            setServerStopped();
            service.unregisterAction(START_OK);
            service.unregisterAction(START_ERROR);
            service.unregisterAction(STOPPED);
         }
      });

      super.start();
      long startTime = System.currentTimeMillis();
      while (!serverStarted) {
         try {
            long waitTime = startTime + service.startTimeout - System.currentTimeMillis();
            if (waitTime <= 0) {
               throw new IllegalStateException("Server did not start within timeout");
            }
            wait(waitTime);
         } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted waiting for server to start", e);
         }
      }
   }

   @Override
   public synchronized void stop() {
      super.stop();
      // TODO: wait for the server to stop?
      if (outputReader != null) {
         try {
            outputReader.join(60000);
         } catch (InterruptedException e) {
            log.warn("Interrupted waiting for the reader");
            Thread.currentThread().interrupt();
         }
         outputReader = null;
      }
   }

   private synchronized void setServerStarted() {
      serverStarted = true;
      serverStopped = false;
      notifyAll();
   }

   private synchronized void setServerStopped() {
      // no serverStarted = false!
      serverStopped = true;
      notifyAll();
   }

}
