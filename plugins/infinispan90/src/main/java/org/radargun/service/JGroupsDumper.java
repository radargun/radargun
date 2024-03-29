package org.radargun.service;

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
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Snapshots JGroups status every 10 seconds (Interval is configurable), printing the information into log.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class JGroupsDumper extends Thread {
   protected static final Log log = LogFactory.getLog(JGroupsDumper.class);
   protected static final List<ProtocolDumper> dumpers = new ArrayList<>();
   protected final ProtocolStack stack;
   protected final long interval;

   public JGroupsDumper(ProtocolStack protocols, long interval) {
      super("JGroupsDumper");
      addDumpers();
      this.interval = interval;
      setDaemon(true);
      stack = protocols;
   }

   protected abstract void addDumpers();

   protected abstract Map<String, Object> getStats(Protocol protocol);

   @Override
   public void run() {
      while (!Thread.interrupted()) {
         Protocol prot = stack.getTopProtocol();
         do {
            log.debug(prot.getName() + ": ");
            for (Map.Entry<String, Object> entry : getStats(prot).entrySet()) {
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
            Thread.sleep(interval);
         } catch (InterruptedException e) {
            break;
         }
      }
      log.info("Dumper interrupted, finishing");
   }

   protected interface ProtocolDumper {
      boolean accepts(Protocol protocol);

      void dump(Protocol protocol);
   }

   protected static void logSorted(String string, String prefix) {
      String[] lines = string.split("\n");
      Arrays.sort(lines);
      for (String line : lines) {
         line = line.trim();
         if (!line.isEmpty()) {
            log.debug(prefix + line);
         }
      }
   }

   protected static class UNICAST3Dumper implements ProtocolDumper {
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

   protected abstract static class TPDumper implements ProtocolDumper {
      private final Set<String> dumped = new HashSet<>();

      @Override
      public boolean accepts(Protocol protocol) {
         return protocol instanceof TP;
      }

      @Override
      public abstract void dump(Protocol protocol);

      protected boolean checkThreadPool(ThreadPoolExecutor tpe, String name) {
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
