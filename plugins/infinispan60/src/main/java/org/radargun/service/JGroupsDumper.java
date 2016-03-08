package org.radargun.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import org.jgroups.protocols.TP;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.TimeScheduler;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.utils.Utils;

/**
 * Snapshots JGroups status every 10 seconds, printing the information into log.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JGroupsDumper extends Thread {
   private static final Log log = LogFactory.getLog(JGroupsDumper.class);
   private final ProtocolStack stack;
   private static final List<ProtocolDumper> dumpers = new ArrayList<ProtocolDumper>();

   static {
      dumpers.add(new UNICAST3Dumper());
      dumpers.add(new TPDumper());
   }

   public JGroupsDumper(ProtocolStack protocols) {
      super("JGroupsDumper");
      setDaemon(true);
      stack = protocols;
   }

   @Override
   public void run() {
      while (!Thread.interrupted()) {
         Protocol prot = stack.getTopProtocol();
         do {
            log.debug(prot.getName() + ": ");
            for (Map.Entry<String, Object> entry : prot.dumpStats().entrySet()) {
               String value = String.valueOf(entry.getValue());
               if (value.indexOf('\n') >= 0) {
                  log.debugf("\t%s = ", entry.getKey());
                  logSorted(value, "\t\t");
               } else {
                  log.debugf("\t%s = %s", entry.getKey(), entry.getValue());
               }
            }
            for (ProtocolDumper dumper : dumpers) {
               if (dumper.accepts(prot)) {
                  dumper.dump(prot);
               }
            }
            prot = prot.getDownProtocol();
         } while (prot != null);
         try {
            Thread.sleep(10000);
         } catch (InterruptedException e) {
            break;
         }
      }
      log.info("Dumper interrupted, finishing");
   }

   private interface ProtocolDumper {
      boolean accepts(Protocol protocol);

      void dump(Protocol protocol);
   }

   private static void logSorted(String string, String prefix) {
      String[] lines = string.split("\n");
      Arrays.sort(lines);
      for (String line : lines) {
         line = line.trim();
         if (!line.isEmpty()) {
            log.debug(prefix + line);
         }
      }
   }

   private static class UNICAST3Dumper implements ProtocolDumper {
      @Override
      public boolean accepts(Protocol protocol) {
         return protocol instanceof UNICAST3;
      }

      @Override
      public void dump(Protocol protocol) {
         log.debug("\tSend table: ");
         logSorted(((UNICAST3) protocol).printSendWindowMessages(), "\t\t");
         log.debug("\tReceive table: ");
         logSorted(((UNICAST3) protocol).printReceiveWindowMessages(), "\t\t");
      }
   }

   private static class TPDumper implements ProtocolDumper {
      private final Set<String> dumped = new HashSet<String>();

      @Override
      public boolean accepts(Protocol protocol) {
         return protocol instanceof TP;
      }

      @Override
      public void dump(Protocol protocol) {
         TP tp = (TP) protocol;
         boolean dump = false;
         dump = checkThreadPool((ThreadPoolExecutor) tp.getDefaultThreadPool(), "Regular") || dump;
         dump = checkThreadPool((ThreadPoolExecutor) tp.getOOBThreadPool(), "OOB") || dump;
         try {
            Field f = TP.class.getDeclaredField("internal_thread_pool");
            f.setAccessible(true);
            dump = checkThreadPool((ThreadPoolExecutor) f.get(tp), "Internal") || dump;
         } catch (Exception e) {
            log.error("Failed to get internal thread pool");
         }
         TimeScheduler timer = tp.getTimer();
         try {
            Field f = timer.getClass().getDeclaredField("pool");
            f.setAccessible(true);
            dump = checkThreadPool((ThreadPoolExecutor) f.get(timer), "Timer") || dump;
         } catch (Exception e) {
            log.error("Failed to get timer thread pool: " + timer.getClass());
         }
         if (dump) Utils.threadDump();
      }

      private boolean checkThreadPool(ThreadPoolExecutor tpe, String name) {
         int threshold = (tpe.getMaximumPoolSize() * 95 / 100) - 1;
         log.infof("%s current: %d, active: %d, core: %d, max: %d, scheduled: %d, completed: %d, queue size: %d", name,
            tpe.getPoolSize(), tpe.getActiveCount(), tpe.getCorePoolSize(), tpe.getMaximumPoolSize(),
            tpe.getTaskCount(), tpe.getCompletedTaskCount(), tpe.getQueue() != null ? tpe.getQueue().size() : -1);
         boolean dump = tpe.getActiveCount() >= threshold || (tpe.getPoolSize() >= threshold && !dumped.contains(name));
         if (dump) dumped.add(name);
         if (tpe.getPoolSize() < threshold) dumped.remove(name);
         return dump;
      }
   }
}
