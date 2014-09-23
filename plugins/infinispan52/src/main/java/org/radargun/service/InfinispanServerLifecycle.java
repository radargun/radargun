package org.radargun.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanServerLifecycle extends ProcessLifecycle {
   private boolean serverStarted;
   private boolean serverStopped;
   private final InfinispanServerService service;
   private final static Pattern START_OK = Pattern.compile(".*\\[org\\.jboss\\.as\\].*started in.*");
   private final static Pattern START_ERROR = Pattern.compile(".*\\[org\\.jboss\\.as\\].*started \\(with errors\\) in.*");
   private final static Pattern STOPPED = Pattern.compile(".*\\[org\\.jboss\\.as\\].*stopped in.*");
   private final List<Runnable> stopListeners = new ArrayList<>();

   public InfinispanServerLifecycle(final InfinispanServerService service) {
      super(service);
      this.service = service;
   }

   @Override
   public void start() {
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
      synchronized (this) {
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
   }

   @Override
   public void stop() {
      super.stop();
      // TODO: wait for the server to stop?
      stopReaders();
   }

   @Override
   public void kill() {
      super.kill();
      stopReaders();
   }

   @Override
   public void killAsync() {
      super.killAsync();
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

   private synchronized void setServerStarted() {
      serverStarted = true;
      serverStopped = false;
      notifyAll();
   }

   private synchronized void setServerStopped() {
      // no serverStarted = false!
      serverStopped = true;
      notifyAll();
      for (Runnable listener : stopListeners) {
         listener.run();
      }
   }

   public synchronized void registerOnStop(Runnable runnable) {
      stopListeners.add(runnable);
   }
}
