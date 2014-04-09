package org.radargun.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * Reads the server process output, looking for specific patterns
 * in order to detect whether the server has already started or stopped.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan60ServerLifecycle extends ProcessLifecycle {
   private ServerOutputReader outputReader;

   public Infinispan60ServerLifecycle(ProcessService service) {
      super(service);
   }

   @Override
   public synchronized void start() {
      super.start();
      long startTime = System.currentTimeMillis();
      while (!outputReader.isServerStarted()) {
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
      outputReader = null;
   }

   @Override
   protected synchronized StreamReader getOutputReader() {
      if (outputReader == null) {
         outputReader = new ServerOutputReader();
      }
      return outputReader;
   }

   protected class ServerOutputReader extends Thread implements StreamReader {
      private boolean serverStarted;
      private BufferedReader reader;

      @Override
      public void setStream(InputStream stream) {
         this.reader = new BufferedReader(new InputStreamReader(stream));
         this.start();
      }

      @Override
      public void run() {
         String line;
         Pattern startedOK = Pattern.compile(".*\\[org\\.jboss\\.as\\].*started in.*");
         Pattern startedError = Pattern.compile(".*\\[org\\.jboss\\.as\\].*started \\(with errors\\) in.*");
         Pattern stopped = Pattern.compile(".*\\[org\\.jboss\\.as\\].*stopped in.*");
         try {
            while ((line = reader.readLine()) != null) {
               if (startedOK.matcher(line).matches()) {
                  setServerStarted();
               }
               if (startedError.matcher(line).matches()) {
                  log.warn("Server started with errors");
                  setServerStarted();
               }
               if (stopped.matcher(line).matches()) {
                  log.error("Server stopped before it started!");
                  setServerStarted();
               }
               System.out.println(line);
            }
         } catch (IOException e) {
            log.error("Failed to read server output", e);
         } finally {
            try {
               reader.close();
            } catch (IOException e) {
               log.error("Failed to close", e);
            }
         }
      }

      private void setServerStarted() {
         synchronized (Infinispan60ServerLifecycle.this) {
            serverStarted = true;
            Infinispan60ServerLifecycle.this.notifyAll();
         }
      }

      public boolean isServerStarted() {
         return serverStarted;
      }
   }
}
