package org.radargun.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.utils.TimeService;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanServerLifecycle extends ProcessLifecycle<InfinispanServerService> {
   private boolean serverStarted;
   private boolean serverStopped;

   private volatile boolean gracefulStop = true;

   public InfinispanServerLifecycle(final InfinispanServerService service) {
      super(service);
   }

   protected Pattern getStartOK() {
      return Pattern.compile(".*\\[org\\.jboss\\.as\\].*started in.*");
   }

   protected Pattern getStartError() {
      return Pattern.compile(".*\\[org\\.jboss\\.as\\].*started in.*");
   }

   protected Pattern getStoped() {
      return Pattern.compile(".*\\[org\\.jboss\\.as\\].*started in.*");
   }

   @Override
   public void start() {
      service.registerAction(getStartOK(), new ProcessService.OutputListener() {
         @Override
         public void run(Matcher m) {
            setServerStarted();
            service.unregisterAction(getStartOK());
            service.unregisterAction(getStartError());
            fireAfterStart();

         }
      });
      service.registerAction(getStartError(), new ProcessService.OutputListener() {
         @Override
         public void run(Matcher m) {
            log.warn("Server started with errors");
            setServerStarted();
            service.unregisterAction(getStartOK());
            service.unregisterAction(getStartError());
            fireAfterStart();
         }
      });
      service.registerAction(getStoped(), new ProcessService.OutputListener() {
         @Override
         public void run(Matcher m) {
            log.error("Server stopped before it started!");
            setServerStarted();
            setServerStopped();
            service.unregisterAction(getStartOK());
            service.unregisterAction(getStartError());
            service.unregisterAction(getStoped());
            fireAfterStop(gracefulStop);

         }
      });
      if (isRunning()) {
         log.warn("Process is already running");
         return;
      }
      fireBeforeStart();
      startInternal();

      long startTime = TimeService.currentTimeMillis();
      synchronized (this) {
         while (!serverStarted) {
            try {
               long waitTime = startTime + service.startTimeout - TimeService.currentTimeMillis();
               if (waitTime <= 0) {
                  throw new IllegalStateException("Server did not start within timeout");
               }
               wait(waitTime);
            } catch (InterruptedException e) {
               throw new IllegalStateException("Interrupted waiting for server to start", e);
            }
         }
      }
      // fireAfterStart is called from output listener
   }

   @Override
   public void stop() {
      if (!isRunning()) {
         log.warn("Process is not running, cannot stop");
         return;
      }
      fireBeforeStop(true);
      gracefulStop = true;
      stopInternal();
      stopReaders();
   }

   @Override
   public void kill() {
      gracefulStop = false;
      Runnable waiting = killAsyncInternal();
      if (waiting == null) return;
      waiting.run();
      stopReaders();
   }

   @Override
   public void killAsync() {
      gracefulStop = false;
      Runnable waiting = killAsyncInternal();
      if (waiting == null) {
         return;
      }
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
