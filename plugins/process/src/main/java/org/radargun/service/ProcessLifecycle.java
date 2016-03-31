package org.radargun.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Killable;
import org.radargun.traits.Lifecycle;
import org.radargun.utils.TimeService;

/**
 * Java runtime does not provide API that would allow us to manage
 * full process tree, that's why we delegate the start/stop/kill
 * handling to OS-specific scripts.
 * So far, only Unix scripts are implemented.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ProcessLifecycle<T extends ProcessService> implements Lifecycle, Killable {

   protected final Log log = LogFactory.getLog(getClass());
   protected final T service;
   protected ProcessOutputReader outputReader, errorReader;

   private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
   private String prefix;
   private String extension;

   public ProcessLifecycle(T service) {
      this.service = service;
      prefix = service.getCommandPrefix();
      extension = service.getCommandSuffix();
   }

   @Override
   public void kill() {
      fireBeforeStop(false);
      try {
         Runnable waiting = killAsyncInternal();
         if (waiting == null) return;
         waiting.run();
      } finally {
         fireAfterStop(false);
      }
   }

   @Override
   public void killAsync() {
      fireBeforeStop(false);
      final Runnable waiting = killAsyncInternal();
      if (waiting == null) {
         fireAfterStop(false);
         return;
      }
      Thread listenerInvoker = new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               waiting.run();
            } finally {
               fireAfterStop(false);
            }
         }
      }, "StopListenerInvoker");
      listenerInvoker.setDaemon(true);
      listenerInvoker.start();
   }

   protected Runnable killAsyncInternal() {
      if (!isRunning()) {
         log.warn("Cannot kill, process is not running");
         return null;
      }
      try {
         fireBeforeStop(false);
         final Process process = new ProcessBuilder().inheritIO()
               .command(Arrays.asList(prefix + "kill" + extension, service.getPid())).start();
         return new Runnable() {
            @Override
            public void run() {
               for (;;) {
                  try {
                     process.waitFor();
                  } catch (InterruptedException e) {
                     log.trace("Interrupted waiting for kill", e);
                  }
                  if (!isRunning()) return;
               }
            }
         };
      } catch (IOException e) {
         log.error("Cannot kill service", e);
         return null;
      }
   }

   @Override
   public void start() {
      if (isRunning()) {
         log.warn("Process is already running");
         return;
      }
      fireBeforeStart();
      try {
         startInternal();
      } finally {
         fireAfterStart();
      }
   }

   protected void startInternal() {
      List<String> command = new ArrayList<String>();
      command.addAll(service.getCommand());
      Map<String, String> env = service.getEnvironment();
      log.info("Environment:\n" + env);
      log.info("Starting with: " + command);
      ProcessBuilder pb = new ProcessBuilder().command(command);
      for (Map.Entry<String, String> envVar : env.entrySet()) {
         pb.environment().put(envVar.getKey(), envVar.getValue());
      }
      StreamWriter inputWriter = getInputWriter();
      StreamReader outputReader = getOutputReader();
      StreamReader errorReader = getErrorReader();
      if (inputWriter == null) {
         pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
      }
      if (outputReader == null) {
         pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      }
      if (errorReader == null) {
         pb.redirectError(ProcessBuilder.Redirect.INHERIT);
      }
      try {
         Process process = pb.start();
         service.setPid(getProcessId(process));
         if (inputWriter != null) inputWriter.setStream(process.getOutputStream());
         if (outputReader != null) outputReader.setStream(process.getInputStream());
         if (errorReader != null) errorReader.setStream(process.getErrorStream());
      } catch (IOException e) {
         log.error("Failed to start", e);
      }
   }

   private static String getProcessId(Process process) {
      Class<?> clazz = process.getClass();
      try {
         if (clazz.getName().equals("java.lang.UNIXProcess")) {
            Field pidField = clazz.getDeclaredField("pid");
            pidField.setAccessible(true);
            Object value = pidField.get(process);
            if (value instanceof Integer) {
               return ((Integer) value).toString();
            }
         } else {
            throw new IllegalArgumentException("Only unix is supported as OS.");
         }
      } catch (SecurityException sx) {
         sx.printStackTrace();
      } catch (NoSuchFieldException e) {
         e.printStackTrace();
      } catch (IllegalArgumentException e) {
         e.printStackTrace();
      } catch (IllegalAccessException e) {
         e.printStackTrace();
      }
      return null;
   }

   @Override
   public void stop() {
      if (!isRunning()) {
         log.warn("Process is not running, cannot stop");
         return;
      }
      fireBeforeStop(true);
      try {
         stopInternal();
      } finally {
         fireAfterStop(true);
         outputReader.interrupt();
         errorReader.interrupt();
      }
   }

   protected void stopInternal() {
      try {
         long startTime = TimeService.currentTimeMillis();
         for (; ; ) {
            String command = service.stopTimeout < 0 || TimeService.currentTimeMillis() < startTime + service.stopTimeout ? "stop" : "kill";
            Process process = new ProcessBuilder().inheritIO().command(Arrays.asList(prefix + command + extension, service.getPid())).start();
            try {
               process.waitFor(service.stopTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
               log.trace("Interrupted waiting for stop", e);
            }
            if (!isRunning()) return;
         }
      } catch (IOException e) {
         log.error("Cannot stop service", e);
      }
   }

   @Override
   public boolean isRunning() {
      if (service.getPid() != null) {
         Process process = null;
         try {
            process = new ProcessBuilder().command("ps", "-ef", service.getPid()).start();
            return process.waitFor() == 0;
         } catch (IOException e) {
            log.error("Cannot determine if process '" + service.getPid() + "' is running", e);
            return false;
         } catch (InterruptedException e) {
            log.error("Script interrupted", e);
            try {
               return process.exitValue() == 0;
            } catch (IllegalThreadStateException itse) {
               return true;
            }
         }
      }
      return false;
   }

   protected synchronized StreamReader getOutputReader() {
      if (outputReader == null) {
         outputReader = new ProcessOutputReader(new LineConsumer() {
            @Override
            public void consume(String line) {
               service.reportOutput(line);
            }
         });
      }
      return outputReader;
   }

   protected synchronized StreamReader getErrorReader() {
      if (errorReader == null) {
         errorReader = new ProcessOutputReader(new LineConsumer() {
            @Override
            public void consume(String line) {
               service.reportError(line);
            }
         });
      }
      return errorReader;
   }

   protected StreamWriter getInputWriter() {
      return null;
   }

   /**
    * Provides a hook for service to read output
    */
   interface StreamReader {
      void setStream(InputStream stream);
   }

   /**
    * Provides a hook for passing input to the process
    */
   interface StreamWriter {
      void setStream(OutputStream stream);
   }

   interface LineConsumer {
      void consume(String line);
   }

   protected static class ProcessOutputReader extends Thread implements StreamReader {

      protected final Log log = LogFactory.getLog(getClass());
      protected BufferedReader reader;
      protected LineConsumer consumer;

      public ProcessOutputReader(LineConsumer consumer) {
         this.consumer = consumer;
      }

      @Override
      public void setStream(InputStream stream) {
         this.reader = new BufferedReader(new InputStreamReader(stream));
         if (!isAlive()) {
            this.start();
         }
      }

      @Override
      public void run() {
         String line;
         try {
            while (!isInterrupted() && (line = reader.readLine()) != null) {
               consumer.consume(line);
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

   public void addListener(Listener listener) {
      listeners.add(listener);
   }

   public void removeListener(Listener listener) {
      listeners.remove(listener);
   }

   // lambdas, wish you were here...
   private interface ListenerRunner {
      void run(Listener listener);
   }

   private void fireListeners(ListenerRunner runner) {
      for (Listener listener : listeners) {
         try {
            runner.run(listener);
         } catch (Exception e) {
            log.error("Listener has thrown an exception", e);
         }
      }
   }

   protected void fireBeforeStart() {
      fireListeners(new ListenerRunner() {
         @Override
         public void run(Listener listener) {
            listener.beforeStart();
         }
      });
   }

   protected void fireAfterStart() {
      fireListeners(new ListenerRunner() {
         @Override
         public void run(Listener listener) {
            listener.afterStart();
         }
      });
   }

   protected void fireBeforeStop(final boolean graceful) {
      fireListeners(new ListenerRunner() {
         @Override
         public void run(Listener listener) {
            listener.beforeStop(graceful);
         }
      });
   }

   protected void fireAfterStop(final boolean graceful) {
      fireListeners(new ListenerRunner() {
         @Override
         public void run(Listener listener) {
            listener.afterStop(graceful);
         }
      });
   }

   public interface Listener {
      void beforeStart();
      void afterStart();
      void beforeStop(boolean graceful);
      void afterStop(boolean graceful);
   }

   public static class ListenerAdapter implements Listener {
      public void beforeStart() {}
      public void afterStart() {}
      public void beforeStop(boolean graceful) {}
      public void afterStop(boolean graceful) {}
   }
}
