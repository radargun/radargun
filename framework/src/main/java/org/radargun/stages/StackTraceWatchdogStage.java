package org.radargun.stages;

import java.util.Arrays;
import java.util.Map;

import org.radargun.DistStageAck;

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
   
   private static final String WATCHDOG = "__watchdog__";
   private static Thread watchDogThread;

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
            log.trace("Watchdog already running");
         }
         watchDogThread = new Thread(new Runnable() {
   
            private Map<Thread, StackTraceElement[]> lastStacks;
            
            @Override
            public void run() {
               while (true) {
                  Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
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
               log.trace(sb.toString());
            }
   
            private boolean isStuck(Thread t, StackTraceElement[] stack) {
               if (lastStacks == null) return false;
               StackTraceElement[] lastStack = lastStacks.get(t);
               if (lastStack == null) return false;
               return Arrays.deepEquals(lastStack, stack);
            }
            
         }, WATCHDOG);
         watchDogThread.setDaemon(true);
         watchDogThread.start();
         log.trace("Started watchdog");
      }
      return newDefaultStageAck();
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
}
