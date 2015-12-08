package org.radargun.service;

import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Parent for Spark's Master & Worker lifecycles
 *
 * @author Matej Cimbora
 */
public abstract class AbstractSparkLifecycle extends ProcessLifecycle<AbstractSparkService> {

   public AbstractSparkLifecycle(AbstractSparkService service) {
      super(service);
   }

   protected void startInternal() {
      super.startInternal();
      service.registerAction(getStartPattern(), m -> {
         long end = TimeService.currentTimeMillis() + service.logCreationTimeout;
         while (TimeService.currentTimeMillis() <= end) {
            String matchedString = m.group();
            String fileName = matchedString.substring(matchedString.lastIndexOf("/"));
            try {
               FileInputStream fileInputStream = new FileInputStream(Paths.get(service.home, "logs", fileName).toFile());
               getOutputReader().setStream(fileInputStream);
               return;
            } catch (FileNotFoundException e) {
               log.error("Log file not created yet");
               Utils.sleep(1000);
               continue;
            }
         }
         throw new IllegalStateException("Failed to attach reader to log file");
      });
   }

   @Override
   protected synchronized StreamReader getOutputReader() {
      if (outputReader == null) {
         outputReader = new FileOutputReader(line -> service.reportOutput(line));
      }
      return outputReader;
   }

   @Override
   protected synchronized StreamReader getErrorReader() {
      if (errorReader == null) {
         errorReader = new FileOutputReader(line -> service.reportError(line));
      }
      return errorReader;
   }

   private static class FileOutputReader extends ProcessOutputReader {

      public FileOutputReader(LineConsumer consumer) {
         super(consumer);
      }

      public void run() {
         String line;
         try {
            while (!isInterrupted()) {
               while ((line = reader.readLine()) != null) {
                  consumer.consume(line);
               }
               try {
                  Thread.currentThread().sleep(1000);
               } catch (InterruptedException e) {
                  log.error("Failed to suspend the thread", e);
               }
            }
         } catch (IOException e) {
            log.error("Failed to read server output", e);
         } finally {
            try {
               reader.close();
            } catch (IOException e) {
               log.error("Failed to close reader", e);
            }
         }
      }
   }

   protected abstract Pattern getStartPattern();
}
