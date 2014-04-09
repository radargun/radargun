package org.radargun.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Killable;
import org.radargun.traits.Lifecycle;

/**
 * Java runtime does not provide API that would allow us to manage
 * full process tree, that's why we delegate the start/stop/kill
 * handling to OS-specific scripts.
 * So far, only Unix scripts are implemented.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ProcessLifecycle implements Lifecycle, Killable {
   protected final Log log = LogFactory.getLog(getClass());
   protected final ProcessService service;

   private String prefix;
   private String extension;
   private String tag;

   public ProcessLifecycle(ProcessService service) {
      this.service = service;
      if (!"unix".equals(service.os)) throw new IllegalArgumentException("Only unix is supported as OS.");
      prefix = "plugins" + File.separator + "process" + File.separator + "bin" + File.separator + service.os + "-";
      extension = "unix".equals(service.os) ? ".sh" : null;
      Random random = new Random();
      StringBuilder sb = new StringBuilder(16);
      for (int i = 0; i < 16; ++i) {
         sb.append((char) (random.nextInt('z' - 'a' + 1) + 'a'));
      }
      tag = sb.toString();
   }

   @Override
   public synchronized void kill() {
      Process process = killAsyncInternal();
      if (process == null) return;
      for (;;) {
         try {
            process.waitFor();
         } catch (InterruptedException e) {
            log.trace("Interrupted waiting for kill", e);
         }
         if (!isRunning()) return;
      }
   }

   @Override
   public synchronized void killAsync() {
      killAsyncInternal();
   }

   private Process killAsyncInternal() {
      if (!isRunning()) {
         log.warn("Cannot kill, process is not running");
         return null;
      }
      try {
         return new ProcessBuilder().inheritIO().command(Arrays.asList(prefix + "kill" + extension, tag)).start();
      } catch (IOException e) {
         log.error("Cannot kill service", e);
      }
      return null;
   }

   @Override
   public synchronized void start() {
      if (isRunning()) {
         log.warn("Process is already running");
         return;
      }
      List<String> command = new ArrayList<String>();
      command.add(prefix + "start" + extension);
      command.add(tag);
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
         if (inputWriter != null) inputWriter.setStream(process.getOutputStream());
         if (outputReader != null) outputReader.setStream(process.getInputStream());
         if (errorReader != null) errorReader.setStream(process.getErrorStream());
      } catch (IOException e) {
         log.error("Failed to start", e);
      }
   }

   @Override
   public synchronized void stop() {
      if (!isRunning()) {
         log.warn("Process is not running, cannot stop");
         return;
      }
      Process process;
      try {
         process = new ProcessBuilder().inheritIO().command(Arrays.asList(prefix + "stop" + extension, tag)).start();
         for (;;) {
            try {
               process.waitFor();
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
   public synchronized boolean isRunning() {
      Process process = null;
      try {
         process = new ProcessBuilder().inheritIO().command(Arrays.asList(prefix + "running" + extension, tag)).start();
         int exitValue = process.waitFor();
         return exitValue == 0;
      } catch (IOException e) {
         log.error("Cannot determine if running", e);
         return false;
      } catch (InterruptedException e) {
         log.error("Script interrupted", e);
         if (process != null) {
            try {
               return process.exitValue() == 0;
            } catch (IllegalThreadStateException itse) {
               return true;
            }
         }
         return true;
      }
   }

   protected StreamReader getOutputReader() {
      return null;
   }

   protected StreamReader getErrorReader() {
      return null;
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
}
