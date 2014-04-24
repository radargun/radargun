package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.AbstractDistStage;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 
 * Periodically check for all thread stack traces and print them out.
 * 
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Debug usage only. Periodically check for all thread stack traces and print them out.")
public class StackTraceWatchdogStage extends AbstractDistStage {

   @Property(converter = TimeConverter.class, doc = "The delay between consecutive checks. Default is 10 seconds.")
   private long period = 10000;

   @Property(doc = "If set, only those threads which have this mask in the name will be checked. Default is not set.")
   private String mask = null;

   @Property(doc = "By default the check will print out only those threads which appear to be stuck. If this is set " +
         "to false all threads will be printed out. Default is true.")
   private boolean onlyStuck = true;

   @Property(doc = "Threads with stack lower or equal to this value are never printed (because usually such threads " +
         "are parked in thread pools). Default is 10.")
   private int shortStack = 10;

   @Property(doc = "If set to true the watchdog will not use standard logging for output but will push the output "
         + "to queue consumed (logged) by another thread. Default is false.")
   private boolean asyncLogging;

   private static final String WATCHDOG = "__watchdog__";
   private static final String WATCHDOG_LOGGING = "__watchdog_logging__";
   private static WatchDogThread watchDogThread;
   private static LoggingThread loggingThread;

   private Queue<String> messageQueue = new LinkedList<String>();
   private DateFormat formatter = new SimpleDateFormat("HH:mm:ss,SSS");

   public StackTraceWatchdogStage() {
      // nada
   }

   public DistStageAck executeOnSlave() {
      if (!log.isTraceEnabled()) {
         log.warn("Trace is not enabled, nothing to do.");
         return newDefaultStageAck();
      }
      synchronized (StackTraceWatchdogStage.class) {
         if (watchDogThread != null) {
            log.warn("Watchdog already running");
         } else {
            watchDogThread = new WatchDogThread();
            watchDogThread.setName(WATCHDOG);
            watchDogThread.setDaemon(true);
            watchDogThread.start();
            log.trace("Started watchdog");
         }
         if (asyncLogging) {
            if (loggingThread != null) {
               log.warn("Logging thread already running");
            } else {
               loggingThread = new LoggingThread();
               loggingThread.setName(WATCHDOG_LOGGING);
               loggingThread.setDaemon(true);
               loggingThread.start();
               log.trace("Started logging thread");
            }
         }
      }
      return newDefaultStageAck();
   }

   private class WatchDogThread extends Thread {
      private Map<Thread, StackTraceElement[]> lastStacks;

      @Override
      public void run() {
         while (true) {
            log("Running check");
            long pre = System.nanoTime();
            Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
            long post = System.nanoTime();
            log("Thread.getAllStackTraces() took " + (post - pre) + " nanoseconds");
            for (Thread t : stacks.keySet()) {
               String name = t.getName();
               if (!name.equals(WATCHDOG) && (mask == null || name.contains(mask))) {
                  StackTraceElement[] stack = stacks.get(t);
                  if (stack.length < shortStack) {
                     continue;
                  }
                  boolean stuck = isStuck(t, stack);
                  if (!onlyStuck || stuck) {
                     traceStack(t, stack, stuck);
                  }
               }
            }
            lastStacks = stacks;
            try {
               Thread.sleep(period);
            } catch (InterruptedException e) {
            }
         }
      }

      private boolean isStuck(Thread t, StackTraceElement[] stack) {
         if (lastStacks == null) return false;
         StackTraceElement[] lastStack = lastStacks.get(t);
         if (lastStack == null) return false;
         return Arrays.deepEquals(lastStack, stack);
      }
   }

   private class LoggingThread extends Thread {
      @Override
      public void run() {
         while (true) {
            String message;
            synchronized (messageQueue) {
               if (messageQueue.isEmpty()) {
                  try {
                     messageQueue.wait();
                  } catch (InterruptedException e) {
                  }
                  continue;
               }
               message = messageQueue.poll();
            }
            log.trace(message);
         }
      }
   }

   private void traceStack(Thread t, StackTraceElement[] stack, boolean stuck) {
      StringBuilder sb = new StringBuilder();
      sb.append("Stack for thread ");
      sb.append(t.getName());
      if (stuck) sb.append("(possibly stuck)");
      sb.append(":\n");
      for (StackTraceElement ste : stack) {
         sb.append(ste.toString());
         sb.append('\n');
      }
      log(sb.toString());
   }

   private void log(String message) {
      if (asyncLogging) {
         synchronized (messageQueue) {
            messageQueue.add(formatter.format(new Date()) + ": " + message);
            messageQueue.notify();
         }
      } else {
         log.trace(message);
      }
   }
}

