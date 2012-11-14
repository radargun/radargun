package org.radargun.stages;

import org.radargun.DistStageAck;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 
 * Periodically check for all thread stack traces and print them out.
 * 
 * @author Radim Vansa <rvansa@redhat.com>
 */
public class StackTraceWatchdogStage extends AbstractDistStage {

   private long period = 10000;
   private String mask = null;
   private boolean onlyStuck = true;
   private int shortStack = 10;
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

   @Override
   public String toString() {
      return "WatchDogStage {period=" + period + ", mask=" + mask + ", onlyStuck=" + onlyStuck + ", " + super.toString();
   }

   /**
    * Period of checking in milliseconds.
    * 
    * @param period
    */
   public void setPeriod(long period) {
      this.period = period;
   }
   
   /**
    * Limit to those threads with name containing the mask.
    * 
    * @param mask
    */
   public void setMask(String mask) {
      this.mask = mask;
   }
   
   /**
    * Print out only those threads that appear stuck 
    * 
    * @param onlyStuck
    */
   public void setOnlyStuck(boolean onlyStuck) {
      this.onlyStuck = onlyStuck;
   }
   
   /**
    * If the stacktrace has less entries it is omitted.
    * Usual for threads parking in threadpool or waiting on socket.
    * 
    * @param length
    */
   public void setShortStack(int length) {
      this.shortStack = length;
   }

   public void setAsyncLogging(boolean asyncLogging) {
      this.asyncLogging = asyncLogging;
   }
}
